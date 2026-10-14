package com.applymate.mail;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Transactional outbox for email.
 *
 * <p>{@link #enqueue} inserts a row in the caller's transaction, so the email exists if
 * and only if the change that caused it committed. {@link #drain} delivers due rows:
 * it claims them with {@code FOR UPDATE SKIP LOCKED}, so any number of backend
 * instances can drain concurrently and each message is sent once. Failures back off
 * exponentially (30s, 1m, 2m ... capped at 1h) and become FAILED after
 * {@link #MAX_ATTEMPTS}, where they stay for inspection instead of retrying forever.
 *
 * <p>Delivery is at-least-once: if the process dies after SMTP accepted a message but
 * before the row is marked SENT, it is sent again. For reminders and reset links a rare
 * duplicate is acceptable; losing one is not.
 */
@Component
public class EmailOutbox {

    private static final Logger log = LoggerFactory.getLogger(EmailOutbox.class);
    static final int MAX_ATTEMPTS = 6;
    static final int BATCH = 20;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(30);
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    private final JdbcTemplate jdbc;
    private final MailTransport transport;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final MeterRegistry metrics;

    public EmailOutbox(JdbcTemplate jdbc, MailTransport transport, TransactionTemplate tx, Clock clock, MeterRegistry metrics) {
        this.jdbc = jdbc;
        this.transport = transport;
        this.tx = tx;
        this.clock = clock;
        this.metrics = metrics;
        Gauge.builder("applymate.outbox.pending", () -> pendingCount()).register(metrics);
    }

    private record Row(long id, String to, String subject, String body, int attempts) {}

    /**
     * Queue an email. With a {@code dedupeKey}, a second message with the same key is
     * dropped, so a job that runs twice (or on two instances) cannot send it twice.
     *
     * @return whether a new message was queued
     */
    public boolean enqueue(String to, String subject, String body, String dedupeKey) {
        Timestamp now = Timestamp.from(Instant.now(clock));
        int inserted = jdbc.update("""
                INSERT INTO outbox_emails (to_address, subject, body, next_attempt_at, dedupe_key, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (dedupe_key) DO NOTHING""", to, subject, body, now, dedupeKey, now);
        return inserted == 1;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:5000}", initialDelayString = "${app.outbox.initial-delay-ms:10000}")
    public void poll() {
        if (!transport.available()) return;
        // Keep going while full batches come back, so a backlog clears without waiting a tick per batch.
        while (drain() == BATCH) {
            // loop
        }
    }

    /** Deliver one batch of due messages. Returns how many were claimed. */
    public int drain() {
        Integer claimed = tx.execute(status -> {
            Instant now = Instant.now(clock);
            List<Row> due = jdbc.query("""
                    SELECT id, to_address, subject, body, attempts FROM outbox_emails
                    WHERE status = 'PENDING' AND next_attempt_at <= ?
                    ORDER BY next_attempt_at, id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED""",
                    (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5)),
                    Timestamp.from(now), BATCH);
            for (Row row : due) deliver(row, now);
            return due.size();
        });
        return claimed == null ? 0 : claimed;
    }

    private void deliver(Row row, Instant now) {
        try {
            transport.send(row.to(), row.subject(), row.body());
            jdbc.update("UPDATE outbox_emails SET status = 'SENT', attempts = attempts + 1, sent_at = ?, last_error = NULL WHERE id = ?",
                    Timestamp.from(now), row.id());
            metrics.counter("applymate.outbox.delivered", "outcome", "sent").increment();
        } catch (Exception e) {
            int attempts = row.attempts() + 1;
            boolean dead = attempts >= MAX_ATTEMPTS;
            Duration wait = backoff(attempts);
            // Exception class only: SMTP error text can echo the message, which may hold a reset link.
            String error = e.getClass().getSimpleName();
            jdbc.update("UPDATE outbox_emails SET attempts = ?, status = ?, next_attempt_at = ?, last_error = ? WHERE id = ?",
                    attempts, dead ? "FAILED" : "PENDING", Timestamp.from(now.plus(wait)), error, row.id());
            metrics.counter("applymate.outbox.delivered", "outcome", dead ? "dead" : "retry").increment();
            if (dead) log.error("Email {} failed {} times and was given up ({})", row.id(), attempts, error);
            else log.warn("Email {} failed (attempt {}), retrying in {}s ({})", row.id(), attempts, wait.toSeconds(), error);
        }
    }

    static Duration backoff(int attempts) {
        Duration wait = BASE_BACKOFF.multipliedBy(1L << Math.min(attempts - 1, 10));
        return wait.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : wait;
    }

    private double pendingCount() {
        try {
            Integer n = jdbc.queryForObject("SELECT count(*) FROM outbox_emails WHERE status = 'PENDING'", Integer.class);
            return n == null ? 0 : n;
        } catch (Exception e) {
            return Double.NaN;
        }
    }
}
