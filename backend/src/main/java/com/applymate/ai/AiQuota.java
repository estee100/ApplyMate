package com.applymate.ai;

import com.applymate.config.AppProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user AI limits. The free tier is shared by everyone on the server, so without a
 * per-user cap one person could spend the whole day's quota in a few minutes.
 *
 * <ul>
 *   <li>Daily: counted in the {@code ai_usage} table, so a restart does not reset it.</li>
 *   <li>Per minute: a sliding window in memory; losing it on restart is harmless.</li>
 * </ul>
 * A request is charged before the model is called (so concurrent requests cannot all
 * slip under the cap) and refunded if the call fails.
 */
@Component
public class AiQuota {

    private final JdbcTemplate jdbc;
    private final AppProperties.Ai config;
    private final Clock clock;
    private final Map<Long, Deque<Instant>> recent = new ConcurrentHashMap<>();

    public AiQuota(JdbcTemplate jdbc, AppProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.config = properties.ai();
        this.clock = clock;
    }

    /** Reserve one request for this user, or throw a 429 explaining which limit was hit. */
    public void acquire(Long userId) {
        Instant now = Instant.now(clock);
        Deque<Instant> window = recent.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(now.minus(Duration.ofMinutes(1)))) {
                window.pollFirst();
            }
            if (window.size() >= config.perMinuteLimitPerUser()) {
                throw AiException.rateLimited("You're sending AI requests quickly. Wait a minute and try again.");
            }
            window.addLast(now);
        }

        // Atomic upsert-and-check: the increment only happens while under the cap.
        int updated = jdbc.update("""
                INSERT INTO ai_usage (user_id, usage_date, request_count) VALUES (?, ?, 1)
                ON CONFLICT (user_id, usage_date) DO UPDATE
                SET request_count = ai_usage.request_count + 1
                WHERE ai_usage.request_count < ?""", userId, today(), config.dailyLimitPerUser());
        if (updated == 0) {
            synchronized (window) { window.pollLast(); }
            throw AiException.rateLimited("You've used today's " + config.dailyLimitPerUser()
                    + " AI requests. They reset at midnight.");
        }
    }

    /** Give back a request whose call failed, so errors do not cost the user quota. */
    public void refund(Long userId) {
        jdbc.update("UPDATE ai_usage SET request_count = GREATEST(request_count - 1, 0) WHERE user_id = ? AND usage_date = ?",
                userId, today());
    }

    public Usage usage(Long userId) {
        Integer used = jdbc.query("SELECT request_count FROM ai_usage WHERE user_id = ? AND usage_date = ?",
                rs -> rs.next() ? rs.getInt(1) : 0, userId, today());
        return new Usage(used == null ? 0 : used, config.dailyLimitPerUser(), config.enabled());
    }

    public boolean aiEnabled() {
        return config.enabled();
    }

    public record Usage(int usedToday, int dailyLimit, boolean enabled) {}

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
