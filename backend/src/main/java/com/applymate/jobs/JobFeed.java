package com.applymate.jobs;

import com.applymate.config.AppProperties;
import com.applymate.model.JobType;
import com.applymate.util.Text;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * The live job feed: both sources in parallel, merged, stored, and cached.
 *
 * <p>Identical searches are served from a shared cache for {@code feed-cache-minutes}.
 * Adzuna's free quota is about 250 calls a day, so without it a handful of users would
 * exhaust it. The cache holds job ids, not cards, so saved state and match scores stay
 * per-user and current.
 */
@Component
public class JobFeed {

    private static final Logger log = LoggerFactory.getLogger(JobFeed.class);
    private static final int MAX_CACHE_ENTRIES = 500;

    private final JobSources sources;
    private final JobStore store;
    private final Duration ttl;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<Query, Entry> cache = new ConcurrentHashMap<>();
    private final MeterRegistry metrics;

    public JobFeed(JobSources sources, JobStore store, AppProperties properties, Clock clock, MeterRegistry metrics) {
        this.sources = sources;
        this.store = store;
        this.ttl = Duration.ofMinutes(properties.jobs().feedCacheMinutes());
        this.clock = clock;
        this.metrics = metrics;
        metrics.gaugeMapSize("applymate.feed.cache.size", java.util.List.of(), cache);
    }

    /** A normalised search. Lowercased so "Java" and "java " share a cache entry. */
    public record Query(String q, String location, JobType jobType, boolean remoteOnly, Integer postedWithinDays, int page) {
        public static Query of(String q, String location, JobType jobType, boolean remoteOnly, Integer days, Integer page) {
            return new Query(
                    Text.normalizeKey(q),
                    remoteOnly ? "" : Text.normalizeKey(location),
                    jobType,
                    remoteOnly,
                    days == null || days <= 0 ? null : Math.min(days, 60),
                    page == null || page < 1 ? 1 : Math.min(page, 10));
        }
    }

    public record Result(List<Long> jobIds, List<String> sources, String notice, boolean hasMore) {}

    private record Entry(Result result, Instant expires) {}

    public Result search(Query query) {
        Instant now = Instant.now(clock);
        Entry cached = cache.get(query);
        if (cached != null && cached.expires().isAfter(now)) {
            metrics.counter("applymate.feed.cache", "result", "hit").increment();
            return cached.result();
        }
        metrics.counter("applymate.feed.cache", "result", "miss").increment();

        Result result = fetch(query);
        // A result produced only from stored jobs is not cached, so recovery is picked up quickly.
        if (!result.sources().isEmpty()) {
            if (cache.size() >= MAX_CACHE_ENTRIES) cache.entrySet().removeIf(e -> !e.getValue().expires().isAfter(now));
            if (cache.size() >= MAX_CACHE_ENTRIES) cache.clear();
            cache.put(query, new Entry(result, now.plus(ttl)));
        }
        return result;
    }

    private Result fetch(Query query) {
        var sourceQuery = new JobSources.Query(query.q(), query.location(), query.jobType(), query.postedWithinDays(), query.page());
        // Himalayas is remote-only, so "remote only" skips Adzuna entirely.
        boolean useAdzuna = sources.adzunaEnabled() && !query.remoteOnly();

        Future<List<NormalizedJob>> adzuna = useAdzuna ? executor.submit(() -> timed("adzuna", () -> sources.searchAdzuna(sourceQuery))) : null;
        Future<List<NormalizedJob>> himalayas = executor.submit(() -> timed("himalayas", () -> sources.searchHimalayas(sourceQuery)));

        List<String> used = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        List<NormalizedJob> adzunaJobs = collect(adzuna, "Adzuna", used, failures);
        List<NormalizedJob> himalayasJobs = collect(himalayas, "Himalayas", used, failures);

        if (used.isEmpty()) {
            // Every source failed: never take the feed down, serve what is already stored.
            List<Long> stored = store.searchStored(query.q(), JobSources.PAGE_SIZE);
            return new Result(stored, List.of(),
                    "Live job sources are unavailable right now, so these are matching jobs from earlier searches.", false);
        }

        List<NormalizedJob> merged = JobSources.merge(adzunaJobs, himalayasJobs);
        List<Long> ids = store.upsert(merged);
        boolean hasMore = adzunaJobs.size() >= JobSources.PAGE_SIZE - 5 || himalayasJobs.size() >= JobSources.PAGE_SIZE - 5;
        String notice = failures.isEmpty() ? null : String.join("; ", failures) + ". Showing results from the other source.";
        if (!sources.adzunaEnabled() && !query.remoteOnly() && notice == null) {
            notice = "Only remote jobs are shown because no Adzuna key is configured.";
        }
        return new Result(ids, used, notice, hasMore);
    }

    /** Latency and outcome per job source. */
    private List<NormalizedJob> timed(String source, java.util.function.Supplier<List<NormalizedJob>> call) {
        Timer.Sample sample = Timer.start(metrics);
        String outcome = "success";
        try {
            return call.get();
        } catch (RuntimeException e) {
            outcome = "error";
            throw e;
        } finally {
            sample.stop(metrics.timer("applymate.jobs.source", "source", source, "outcome", outcome));
        }
    }

    private static List<NormalizedJob> collect(Future<List<NormalizedJob>> future, String name,
                                               List<String> used, List<String> failures) {
        if (future == null) return List.of();
        try {
            List<NormalizedJob> jobs = future.get(25, TimeUnit.SECONDS);
            used.add(name);
            return jobs;
        } catch (ExecutionException e) {
            String message = e.getCause() instanceof JobSources.SourceException s ? s.getMessage() : name + " failed";
            log.warn("Job source failed: {}", message);
            failures.add(message);
        } catch (TimeoutException e) {
            future.cancel(true);
            failures.add(name + " timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failures.add(name + " was interrupted");
        }
        return List.of();
    }
}
