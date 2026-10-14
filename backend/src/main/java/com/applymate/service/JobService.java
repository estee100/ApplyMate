package com.applymate.service;

import com.applymate.ai.AiService;
import com.applymate.ai.Schemas.JobExtraction;
import com.applymate.ai.Schemas.MatchAnalysis;
import com.applymate.dto.JobDtos.*;
import com.applymate.error.ApiException;
import com.applymate.jobs.*;
import com.applymate.model.*;
import com.applymate.repository.*;
import com.applymate.util.Text;
import com.applymate.util.Urls;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JobService {

    private final JobRepository jobs;
    private final UserJobRepository userJobs;
    private final SavedJobRepository savedJobs;
    private final JobMatchRepository matches;
    private final JobApplicationRepository applications;
    private final GeneratedDocumentRepository documents;
    private final JobApplicationService applicationService;
    private final ResumeService resumes;
    private final JobFeed feed;
    private final JobStore store;
    private final SafeFetcher fetcher;
    private final JobPageParser parser;
    private final AiService ai;
    private final CurrentUser currentUser;
    private final TransactionTemplate tx;
    private final Clock clock;

    public JobService(JobRepository jobs, UserJobRepository userJobs, SavedJobRepository savedJobs,
                      JobMatchRepository matches, JobApplicationRepository applications,
                      GeneratedDocumentRepository documents, JobApplicationService applicationService,
                      ResumeService resumes, JobFeed feed, JobStore store, SafeFetcher fetcher, JobPageParser parser,
                      AiService ai, CurrentUser currentUser, TransactionTemplate tx, Clock clock) {
        this.jobs = jobs;
        this.userJobs = userJobs;
        this.savedJobs = savedJobs;
        this.matches = matches;
        this.applications = applications;
        this.documents = documents;
        this.applicationService = applicationService;
        this.resumes = resumes;
        this.feed = feed;
        this.store = store;
        this.fetcher = fetcher;
        this.parser = parser;
        this.ai = ai;
        this.currentUser = currentUser;
        this.tx = tx;
        this.clock = clock;
    }

    // --- Lists ---------------------------------------------------------------

    public FeedResponse feed(String q, String location, JobType jobType, boolean remoteOnly, Integer days, Integer page) {
        Long userId = currentUser.id();
        JobFeed.Query query = JobFeed.Query.of(q, location, jobType, remoteOnly, days, page);
        JobFeed.Result result = feed.search(query);
        return new FeedResponse(cards(userId, load(result.jobIds())), result.sources(), result.notice(),
                result.hasMore(), query.page());
    }

    public List<JobCard> mine() {
        Long userId = currentUser.id();
        return cards(userId, userJobs.findForUser(userId).stream().map(UserJob::getJob).toList());
    }

    public List<JobCard> saved() {
        Long userId = currentUser.id();
        return cards(userId, savedJobs.findForUser(userId).stream().map(SavedJob::getJob).toList());
    }

    public void save(Long jobId) {
        Long userId = currentUser.id();
        Job job = visible(jobId, userId);
        if (savedJobs.findByUserIdAndJobId(userId, jobId).isEmpty()) {
            try {
                savedJobs.save(new SavedJob(userId, job));
            } catch (org.springframework.dao.DataIntegrityViolationException ignored) {
                // A double click saved it already; saving is idempotent.
            }
        }
    }

    public void unsave(Long jobId) {
        Long userId = currentUser.id();
        savedJobs.findByUserIdAndJobId(userId, jobId).ifPresent(savedJobs::delete);
    }

    public JobDetail detail(Long jobId) {
        Long userId = currentUser.id();
        Job job = visible(jobId, userId);
        Resume resume = resumes.active(userId).orElse(null);
        MatchResponse match = resume == null ? null : matches.findByUserIdAndJobIdAndResumeId(userId, jobId, resume.getId())
                .map(JobService::toResponse).orElse(null);
        var application = applications.findForUserAndJob(userId, jobId)
                .flatMap(a -> applications.findOneForUser(a.getId(), userId))
                .map(applicationService::toResponse).orElse(null);
        var docs = documents.findForUserAndJob(userId, jobId).stream()
                .map(d -> new DocumentSummary(d.getId(), d.getKind(), d.getModelUsed(), d.getEditedContent() != null,
                        d.getCreatedAt(), d.getUpdatedAt()))
                .toList();
        return new JobDetail(cards(userId, List.of(job)).getFirst(), job.getDescription(), analyzable(job), match,
                application, docs, resume != null);
    }

    // --- Adding jobs ---------------------------------------------------------

    /** Fetch and parse a pasted link. JSON-LD and known containers first; the model only as a last resort. */
    public JobDetail pasteLink(String rawUrl) {
        Long userId = currentUser.id();
        SafeFetcher.Page page = fetcher.fetch(rawUrl);
        JobPageParser.Parsed parsed = parser.parse(page.html());

        String title, company, location, description;
        if (parsed != null && parsed.usable() && !Text.isBlank(parsed.title()) && !Text.isBlank(parsed.company())) {
            title = parsed.title();
            company = parsed.company();
            location = parsed.location();
            description = parsed.description();
        } else {
            JobExtraction x = ai.extractJob(userId, parser.pageText(page.html(), 40_000), page.finalUrl()).value();
            title = firstNonBlank(x.title(), parsed == null ? null : parsed.title());
            company = firstNonBlank(x.company(), parsed == null ? null : parsed.company());
            location = firstNonBlank(x.location(), parsed == null ? null : parsed.location());
            description = firstNonBlank(parsed != null && parsed.usable() ? parsed.description() : null, x.description());
        }
        if (Text.isBlank(title) || Text.isBlank(description) || description.length() < 50) {
            throw ApiException.unprocessable(
                    "We couldn't find a job advert on that page. Copy the job description and paste it as text instead.");
        }
        String url = Urls.safeHttp(page.finalUrl());
        NormalizedJob job = new NormalizedJob(JobSource.PASTED, Text.sha256(canonical(url)),
                Text.clip(title, 500), Text.clipOrEmpty(company == null ? "Unknown company" : company, 255),
                null, Text.clip(location, 255), null, url, Urls.publisherFor(url), null, LocalDate.now(clock),
                Text.clip(description, JobSources.MAX_DESCRIPTION));
        return addForUser(userId, job);
    }

    /** For sites that block fetching. Costs no AI quota. */
    public JobDetail pasteText(PasteTextRequest req) {
        Long userId = currentUser.id();
        String url = Text.isBlank(req.url()) ? null : Urls.safeHttp(req.url());
        if (!Text.isBlank(req.url()) && url == null) throw ApiException.badRequest("The job link must start with http:// or https://");
        String description = Text.clip(req.description(), JobSources.MAX_DESCRIPTION);
        // Same link (or same text, without one) reuses the row instead of duplicating it.
        String key = Text.sha256(url != null ? canonical(url) : "text:" + Text.normalizeKey(description));
        NormalizedJob job = new NormalizedJob(JobSource.PASTED, key, Text.clip(req.title(), 500), Text.clip(req.company(), 255),
                null, Text.clip(req.location(), 255), null, url, Urls.publisherFor(url), null, LocalDate.now(clock), description);
        return addForUser(userId, job);
    }

    private JobDetail addForUser(Long userId, NormalizedJob normalized) {
        Long jobId = tx.execute(status -> {
            Long id = store.upsert(normalized);
            if (!userJobs.existsByUserIdAndJobId(userId, id)) {
                userJobs.save(new UserJob(userId, jobs.getReferenceById(id)));
            }
            return id;
        });
        return detail(jobId);
    }

    public void removeMine(Long jobId) {
        Long userId = currentUser.id();
        userJobs.findByUserIdAndJobId(userId, jobId).ifPresent(userJobs::delete);
    }

    // --- Match analysis ------------------------------------------------------

    /** Cached per (user, job, resume); only {@code refresh} spends a request on a job already scored. */
    public MatchResponse match(Long jobId, boolean refresh) {
        Long userId = currentUser.id();
        Job job = visible(jobId, userId);
        if (!analyzable(job)) throw ApiException.unprocessable("This job has no description to analyse.");
        Resume resume = resumes.requireActive(userId);

        Optional<JobMatch> cached = matches.findByUserIdAndJobIdAndResumeId(userId, jobId, resume.getId());
        if (cached.isPresent() && !refresh) return toResponse(cached.get());

        var result = ai.analyzeMatch(userId, resume.getText(), job.getTitle(), job.getCompany(), job.getDescription());
        MatchAnalysis m = result.value();
        JobMatch saved = tx.execute(status -> {
            JobMatch row = matches.findByUserIdAndJobIdAndResumeId(userId, jobId, resume.getId()).orElseGet(JobMatch::new);
            row.setUserId(userId);
            row.setJobId(jobId);
            row.setResumeId(resume.getId());
            row.setMatchPercentage(m.matchPercentage());
            row.setRequirementsMet(Text.cleanList(m.requirementsMet(), 30, 300));
            row.setRequirementsMissing(Text.cleanList(m.requirementsMissing(), 30, 300));
            row.setRationale(Text.clip(m.rationale(), 2000));
            row.setModelUsed(result.model());
            row.setGeneratedAt(Instant.now(clock));
            return matches.save(row);
        });
        return toResponse(saved);
    }

    // --- Shared helpers --------------------------------------------------------

    /**
     * Feed jobs are public listings, but a pasted job may hold text only its owner should
     * see, so it is visible only to users who pasted it. A miss is a 404 either way.
     */
    public Job visible(Long jobId, Long userId) {
        Job job = jobs.findById(jobId).orElseThrow(() -> ApiException.notFound("Job"));
        if (job.getSource() == JobSource.PASTED && !userJobs.existsByUserIdAndJobId(userId, jobId)) {
            throw ApiException.notFound("Job");
        }
        return job;
    }

    public static boolean analyzable(Job job) {
        return job.getDescription() != null && job.getDescription().length() >= 50;
    }

    /** Cards for a list of jobs in a fixed number of queries, whatever the list length. */
    public List<JobCard> cards(Long userId, List<Job> list) {
        if (list.isEmpty()) return List.of();
        List<Long> ids = list.stream().map(Job::getId).toList();
        Set<Long> saved = savedJobs.savedJobIds(userId, ids);
        Map<Long, JobApplication> applied = applications.findForUserAndJobs(userId, ids).stream()
                .collect(Collectors.toMap(a -> a.getJob().getId(), Function.identity(), (a, b) -> a));
        Map<Long, JobMatch> scored = resumes.active(userId)
                .map(r -> matches.findByUserIdAndResumeIdAndJobIdIn(userId, r.getId(), ids).stream()
                        .collect(Collectors.toMap(JobMatch::getJobId, Function.identity(), (a, b) -> a)))
                .orElse(Map.of());
        List<JobCard> out = new ArrayList<>(list.size());
        for (Job job : list) {
            JobApplication app = applied.get(job.getId());
            JobMatch match = scored.get(job.getId());
            out.add(new JobCard(job.getId(), job.getTitle(), job.getCompany(), job.getCompanyLogoUrl(), job.getLocation(),
                    job.getSalary(), job.getUrl(), job.getPublisher(), job.getSource(), job.getJobType(), job.getPostedDate(),
                    saved.contains(job.getId()),
                    app == null ? null : app.getId(),
                    app == null ? null : app.getStatus(),
                    match == null ? null : new MatchSummary(match.getMatchPercentage(),
                            match.getRequirementsMet().size(), match.getRequirementsMissing().size())));
        }
        return out;
    }

    /** Jobs by id, in the order given. */
    public List<Job> load(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        Map<Long, Job> byId = jobs.findAllById(ids).stream().collect(Collectors.toMap(Job::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private static MatchResponse toResponse(JobMatch m) {
        return new MatchResponse(m.getMatchPercentage(), m.getRequirementsMet(), m.getRequirementsMissing(),
                m.getRationale(), m.getModelUsed(), m.getGeneratedAt());
    }

    /**
     * The URL without its fragment or trailing slash, so trivial variants dedupe. Only the
     * scheme and host are lowercased: paths and query strings can carry case-sensitive ids.
     */
    private static String canonical(String url) {
        if (url == null) return "";
        try {
            URI u = URI.create(url);
            String path = u.getRawPath() == null ? "" : u.getRawPath().replaceAll("/+$", "");
            return u.getScheme().toLowerCase(Locale.ROOT) + "://" + u.getHost().toLowerCase(Locale.ROOT) + path
                    + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery());
        } catch (Exception e) {
            return url;
        }
    }

    private static String firstNonBlank(String a, String b) {
        return !Text.isBlank(a) ? a : b;
    }
}
