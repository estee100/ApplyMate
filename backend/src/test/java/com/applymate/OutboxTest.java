package com.applymate;

import com.applymate.mail.EmailOutbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxTest extends IntegrationTest {

    @Autowired
    EmailOutbox outbox;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanOutbox() {
        jdbc.update("DELETE FROM outbox_emails");
        when(mailTransport.available()).thenReturn(true);
    }

    private Map<String, Object> row(String to) {
        return jdbc.queryForMap("SELECT status, attempts, last_error FROM outbox_emails WHERE to_address = ?", to);
    }

    @Test
    void queuedEmailIsDeliveredOnce() throws Exception {
        String to = "a-" + UUID.randomUUID() + "@example.com";
        outbox.enqueue(to, "Hi", "Body", null);
        assertThat(outbox.drain()).isEqualTo(1);
        assertThat(outbox.drain()).isZero();
        verify(mailTransport, times(1)).send(to, "Hi", "Body");
        assertThat(row(to).get("status")).isEqualTo("SENT");
    }

    @Test
    void failuresBackOffThenGiveUp() throws Exception {
        String to = "b-" + UUID.randomUUID() + "@example.com";
        doThrow(new IllegalStateException("smtp down: secret reset link")).when(mailTransport).send(eq(to), any(), any());
        outbox.enqueue(to, "Hi", "Body", null);

        outbox.drain();
        assertThat(row(to).get("attempts")).isEqualTo(1);
        // Not retried before its backoff elapses.
        outbox.drain();
        assertThat(row(to).get("attempts")).isEqualTo(1);
        // The stored error never contains the exception message, which could echo the email.
        assertThat(row(to).get("last_error")).isEqualTo("IllegalStateException");

        for (int i = 0; i < 10; i++) {
            clock.advance(Duration.ofHours(2));
            outbox.drain();
        }
        assertThat(row(to).get("status")).isEqualTo("FAILED");
        assertThat(row(to).get("attempts")).isEqualTo(6);
    }

    @Test
    void dedupeKeyDropsDuplicates() {
        String key = "reminder:" + UUID.randomUUID();
        assertThat(outbox.enqueue("c@example.com", "Hi", "Body", key)).isTrue();
        assertThat(outbox.enqueue("c@example.com", "Hi", "Body", key)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_emails WHERE dedupe_key = ?", Integer.class, key)).isEqualTo(1);
    }

    @Test
    void concurrentWorkersSendEachEmailExactlyOnce() throws Exception {
        List<String> recipients = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            String to = "d" + i + "-" + UUID.randomUUID() + "@example.com";
            recipients.add(to);
            outbox.enqueue(to, "Hi", "Body", null);
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> workers = new ArrayList<>();
        for (int w = 0; w < 4; w++) {
            workers.add(pool.submit(() -> {
                start.await();
                while (outbox.drain() > 0) {
                    // keep draining
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : workers) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();

        // FOR UPDATE SKIP LOCKED: no two workers ever claim the same row.
        for (String to : recipients) verify(mailTransport, times(1)).send(eq(to), any(), any());
    }

    @Test
    void passwordResetEmailGoesThroughTheOutbox() throws Exception {
        String email = "reset-" + UUID.randomUUID() + "@example.com";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/register")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/forgot-password")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
        assertThat(row(email).get("status")).isEqualTo("PENDING");
        outbox.drain();
        verify(mailTransport).send(eq(email), eq("Reset your ApplyMate password"), contains("/reset-password?token="));
    }
}
