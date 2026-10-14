package com.applymate;

import com.applymate.service.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest extends IntegrationTest {

    @Autowired
    RateLimiter limiter;

    @Test
    void concurrentRequestsCannotOverspendABucket() throws Exception {
        String key = "test:" + UUID.randomUUID();
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                if (limiter.tryAcquire(key, 10, Duration.ofHours(1))) allowed.incrementAndGet();
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        // Exactly the bucket size: the atomic upsert admits no more, however the requests interleave.
        assertThat(allowed.get()).isEqualTo(10);
    }

    @Test
    void bucketsRefillOverTime() {
        String key = "test:" + UUID.randomUUID();
        for (int i = 0; i < 6; i++) assertThat(limiter.tryAcquire(key, 6, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.tryAcquire(key, 6, Duration.ofMinutes(1))).isFalse();

        clock.advance(Duration.ofSeconds(10)); // refills one token every 10 seconds
        assertThat(limiter.tryAcquire(key, 6, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.tryAcquire(key, 6, Duration.ofMinutes(1))).isFalse();

        clock.advance(Duration.ofHours(1)); // never refills past capacity
        for (int i = 0; i < 6; i++) assertThat(limiter.tryAcquire(key, 6, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.tryAcquire(key, 6, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void keysAreIndependentAndResettable() {
        String a = "test:" + UUID.randomUUID();
        String b = "test:" + UUID.randomUUID();
        assertThat(limiter.tryAcquire(a, 1, Duration.ofHours(1))).isTrue();
        assertThat(limiter.tryAcquire(a, 1, Duration.ofHours(1))).isFalse();
        assertThat(limiter.tryAcquire(b, 1, Duration.ofHours(1))).isTrue();
        limiter.reset(a);
        assertThat(limiter.tryAcquire(a, 1, Duration.ofHours(1))).isTrue();
    }
}
