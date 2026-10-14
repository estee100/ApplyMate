package com.applymate.service;

import com.applymate.error.ApiException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window limiter keyed by any string (user id, email, IP).
 * Single-instance only: running several backend replicas would need Redis or similar.
 */
@Component
public class RateLimiter {

    private static final int MAX_KEYS = 50_000;

    private final Clock clock;
    private final Map<String, Deque<Instant>> windows = new ConcurrentHashMap<>();

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Record one hit for {@code key}, or throw 429 if it already had {@code limit} in {@code window}. */
    public void check(String key, int limit, Duration window, String message) {
        Instant now = Instant.now(clock);
        if (windows.size() > MAX_KEYS) windows.clear(); // bounded memory under a flood of distinct keys
        Deque<Instant> hits = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (hits) {
            Instant cutoff = now.minus(window);
            while (!hits.isEmpty() && !hits.peekFirst().isAfter(cutoff)) hits.pollFirst();
            if (hits.size() >= limit) throw ApiException.tooManyRequests(message);
            hits.addLast(now);
        }
    }

    public void reset(String key) {
        windows.remove(key);
    }
}
