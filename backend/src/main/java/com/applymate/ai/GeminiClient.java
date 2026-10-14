package com.applymate.ai;

import com.applymate.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gemini over its REST API, on the free tier.
 *
 * <p>Each free-tier model has its own small quota, so a call walks the configured model
 * chain: a quota error moves on to the next model, and a model out of quota is skipped
 * for a while instead of being asked again. A busy model (503) is retried in place a
 * couple of times, so one request never waits through every model.
 */
@Component
public class GeminiClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final Duration[] BACKOFF = {Duration.ofSeconds(2), Duration.ofSeconds(5)};
    private static final Duration DAILY_SKIP = Duration.ofHours(1);
    private static final Duration MINUTE_SKIP = Duration.ofSeconds(60);
    private static final Pattern RETRY_DELAY = Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+)");

    private final AppProperties.Ai config;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private Duration[] backoff = BACKOFF;

    /** For tests: retry without waiting seconds. */
    void setBackoff(Duration... backoff) {
        this.backoff = backoff;
    }

    /** model -> skip until; plus whether the skip was for the daily quota. */
    private final Map<String, Skip> exhausted = new ConcurrentHashMap<>();

    private record Skip(Instant until, boolean daily) {}

    public GeminiClient(AppProperties properties, ObjectMapper mapper, Clock clock, MeterRegistry metrics) {
        this.config = properties.ai();
        this.mapper = mapper;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Override
    public <T> Result<T> generate(String label, String system, String prompt, Class<T> schema) {
        if (!config.enabled()) throw AiException.disabled();

        List<String> models = config.models().stream().filter(this::available).toList();
        if (models.isEmpty()) throw AiException.rateLimited(quotaMessage());

        String body = requestBody(system, prompt, schema);
        boolean hitQuota = false;
        for (String model : models) {
            Timer.Sample sample = Timer.start(metrics);
            HttpResponse<String> response;
            try {
                response = callWithRetry(label, model, body);
            } catch (RuntimeException e) {
                record(sample, label, model, "unavailable");
                throw e;
            }
            int status = response.statusCode();
            if (status == 200) {
                try {
                    T value = parse(label, response.body(), schema);
                    record(sample, label, model, "success");
                    return new Result<>(value, model);
                } catch (RuntimeException e) {
                    record(sample, label, model, "unreadable");
                    throw e;
                }
            }
            String text = response.body() == null ? "" : response.body();
            if (status == 429 || text.contains("RESOURCE_EXHAUSTED")) {
                record(sample, label, model, "quota");
                boolean daily = text.contains("PerDay");
                exhausted.put(model, new Skip(Instant.now(clock).plus(daily ? DAILY_SKIP : retryDelay(text)), daily));
                log.warn("Gemini {}: {} quota reached on {}, trying the next model", label, daily ? "daily" : "per-minute", model);
                hitQuota = true;
                continue;
            }
            if (status == 404) {
                record(sample, label, model, "model_missing");
                log.error("Gemini model {} is not available on this key", model);
                continue;
            }
            record(sample, label, model, status >= 500 ? "unavailable" : "error");
            if (status >= 500) throw AiException.busy();
            // Status and a prefix only: the body never contains the key, but stay terse.
            log.error("Gemini {} failed on {}: HTTP {} {}", label, model, status, text.substring(0, Math.min(300, text.length())));
            throw AiException.failed();
        }
        if (hitQuota) throw AiException.rateLimited(quotaMessage());
        throw AiException.failed();
    }

    /** Latency and outcome per model and feature: shows which model answers and how often quota runs out. */
    private void record(Timer.Sample sample, String label, String model, String outcome) {
        sample.stop(Timer.builder("applymate.ai.request")
                .description("Gemini calls by feature, model and outcome")
                .tag("feature", label).tag("model", model).tag("outcome", outcome)
                .register(metrics));
    }

    private HttpResponse<String> callWithRetry(String label, String model, String body) {
        String endpoint = config.baseUrl() + "/models/" + URLEncoder.encode(model, StandardCharsets.UTF_8) + ":generateContent";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                // Header, not query string, so the key never appears in a logged URL.
                .header("x-goog-api-key", config.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        for (int attempt = 0; ; attempt++) {
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 503 && attempt < backoff.length) {
                    log.warn("Gemini {} overloaded on {}, retrying", label, model);
                    Thread.sleep(backoff[attempt].toMillis());
                    continue;
                }
                return response;
            } catch (IOException e) {
                if (attempt < backoff.length) continue;
                log.error("Gemini {} unreachable: {}", label, e.getClass().getSimpleName());
                throw AiException.busy();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw AiException.busy();
            }
        }
    }

    private String requestBody(String system, String prompt, Class<?> schema) {
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", system))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", ResponseSchema.of(schema)
                )
        );
        try {
            return mapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T parse(String label, String raw, Class<T> schema) {
        try {
            JsonNode root = mapper.readTree(raw);
            JsonNode usage = root.path("usageMetadata");
            log.info("Gemini {}: prompt={} output={} total={}", label,
                    usage.path("promptTokenCount").asText("?"),
                    usage.path("candidatesTokenCount").asText("?"),
                    usage.path("totalTokenCount").asText("?"));
            StringBuilder text = new StringBuilder();
            for (JsonNode part : root.path("candidates").path(0).path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false)) text.append(part.path("text").asText(""));
            }
            if (text.isEmpty()) {
                // A safety block or an empty candidate both land here.
                log.warn("Gemini {} returned no text (finishReason={})", label,
                        root.path("candidates").path(0).path("finishReason").asText("?"));
                throw AiException.unreadable();
            }
            return mapper.readValue(text.toString(), schema);
        } catch (IOException e) {
            throw AiException.unreadable();
        }
    }

    private boolean available(String model) {
        Skip skip = exhausted.get(model);
        return skip == null || !Instant.now(clock).isBefore(skip.until());
    }

    private String quotaMessage() {
        boolean allDaily = config.models().stream().allMatch(m -> exhausted.containsKey(m) && exhausted.get(m).daily());
        return allDaily
                ? "The free AI quota is used up for today. It resets at midnight Pacific time."
                : "The AI free tier is busy. Wait a minute and try again.";
    }

    private static Duration retryDelay(String body) {
        Matcher m = RETRY_DELAY.matcher(body);
        return m.find() ? Duration.ofSeconds(Long.parseLong(m.group(1))) : MINUTE_SKIP;
    }
}
