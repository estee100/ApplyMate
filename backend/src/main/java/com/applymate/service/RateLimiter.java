package com.applymate.service;

import com.applymate.error.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Token-bucket rate limiter stored in Postgres, so every backend instance enforces the
 * same limits (an in-memory limiter lets N replicas allow N times the traffic).
 *
 * <p>Each check is one atomic statement: the upsert computes the refilled balance from
 * the time since the last hit and only spends a token if at least one is available
 * (the {@code WHERE} on the conflict branch). There is no read-then-write window, so
 * concurrent requests can never overspend a bucket. A brand-new key starts full.
 *
 * <p>Elapsed time is clamped at zero and {@code updated_at} never moves backwards, so
 * clock skew between instances (or an NTP step) cannot drain or overfill a bucket.
 */
@Component
public class RateLimiter {

    private static final String SPEND = """
            INSERT INTO rate_limit_buckets AS b (bucket_key, tokens, updated_at)
            VALUES (?, ? - 1, ?)
            ON CONFLICT (bucket_key) DO UPDATE SET
                tokens = LEAST(?, b.tokens + GREATEST(0, EXTRACT(EPOCH FROM (EXCLUDED.updated_at - b.updated_at))) * ?) - 1,
                updated_at = GREATEST(b.updated_at, EXCLUDED.updated_at)
            WHERE LEAST(?, b.tokens + GREATEST(0, EXTRACT(EPOCH FROM (EXCLUDED.updated_at - b.updated_at))) * ?) >= 1
            RETURNING tokens""";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public RateLimiter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Spend one token from {@code key}'s bucket, which allows {@code limit} requests per
     * {@code window} with bursts up to {@code limit}; throws 429 when it is empty.
     */
    public void check(String key, int limit, Duration window, String message) {
        if (!tryAcquire(key, limit, window)) throw ApiException.tooManyRequests(message);
    }

    public boolean tryAcquire(String key, int limit, Duration window) {
        double refillPerSecond = limit / (double) window.toMillis() * 1000.0;
        Timestamp now = Timestamp.from(Instant.now(clock));
        List<Double> left = jdbc.queryForList(SPEND, Double.class,
                key, (double) limit, now, (double) limit, refillPerSecond, (double) limit, refillPerSecond);
        return !left.isEmpty();
    }

    /** Forget a bucket, e.g. clear failed-login attempts after a successful login. */
    public void reset(String key) {
        jdbc.update("DELETE FROM rate_limit_buckets WHERE bucket_key = ?", key);
    }

    /** Buckets idle for a day are full again anyway; dropping them keeps the table small. */
    @Scheduled(cron = "${app.rate-limit.cleanup-cron:0 15 * * * *}")
    public void purgeIdle() {
        jdbc.update("DELETE FROM rate_limit_buckets WHERE updated_at < ?", Timestamp.from(Instant.now(clock).minus(Duration.ofDays(1))));
    }
}
