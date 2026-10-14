package com.applymate.ai;

import com.applymate.ai.Schemas.MatchAnalysis;
import com.applymate.config.AppProperties;
import com.applymate.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The real Gemini client against a local fake server, so retries, model fallback,
 * quota memory, parsing and metrics are tested over actual HTTP.
 */
class GeminiClientTest {

    private record Reply(int status, String body) {}
    private record Seen(String path, String apiKey, String query, JsonNode body) {}

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Deque<Reply>> script = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<Seen> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
    private HttpServer server;
    private SimpleMeterRegistry metrics;
    private GeminiClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String model = path.substring(path.indexOf("/models/") + 8, path.indexOf(':'));
            seen.add(new Seen(path, exchange.getRequestHeaders().getFirst("x-goog-api-key"),
                    exchange.getRequestURI().getQuery(), mapper.readTree(exchange.getRequestBody())));
            Reply reply = script.getOrDefault(model, new ArrayDeque<>()).poll();
            if (reply == null) reply = new Reply(500, "{}");
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        metrics = new SimpleMeterRegistry();
        var props = new AppProperties("http://localhost:3000", List.of(),
                new AppProperties.Ai("secret-key", List.of("model-a", "model-b"), 25, 6,
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta"),
                new AppProperties.Jobs("", "", "ca", 180), new AppProperties.Mail(false, "x"));
        client = new GeminiClient(props, mapper, Clock.systemUTC(), metrics);
        client.setBackoff(Duration.ofMillis(5), Duration.ofMillis(5));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void queue(String model, int status, String body) {
        script.computeIfAbsent(model, k -> new ArrayDeque<>()).add(new Reply(status, body));
    }

    private static String ok(String json) {
        String escaped = json.replace("\\", "\\\\").replace("\"", "\\\"");
        return """
                {"candidates":[{"content":{"parts":[{"text":"thinking...","thought":true},{"text":"%s"}]},"finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":5,"totalTokenCount":15}}""".formatted(escaped);
    }

    private static final String MATCH = "{\"matchPercentage\":72,\"requirementsMet\":[\"Java\"],\"requirementsMissing\":[\"Go\"],\"rationale\":\"ok\"}";

    private double count(String model, String outcome) {
        var timer = metrics.find("applymate.ai.request").tag("model", model).tag("outcome", outcome).timer();
        return timer == null ? 0 : timer.count();
    }

    @Test
    void sendsStructuredRequestWithKeyInHeaderAndSkipsThoughtParts() {
        queue("model-a", 200, ok(MATCH));
        var result = client.generate("match-analysis", "SYSTEM RULES", "USER TURN", MatchAnalysis.class);

        assertThat(result.model()).isEqualTo("model-a");
        assertThat(result.value().matchPercentage()).isEqualTo(72);
        Seen request = seen.getFirst();
        assertThat(request.apiKey()).isEqualTo("secret-key");
        assertThat(request.query()).isNull(); // the key never goes in the URL, where it could be logged
        assertThat(request.body().at("/systemInstruction/parts/0/text").asText()).isEqualTo("SYSTEM RULES");
        assertThat(request.body().at("/contents/0/parts/0/text").asText()).isEqualTo("USER TURN");
        assertThat(request.body().at("/generationConfig/responseMimeType").asText()).isEqualTo("application/json");
        assertThat(request.body().at("/generationConfig/responseSchema/properties/matchPercentage/type").asText()).isEqualTo("INTEGER");
        assertThat(count("model-a", "success")).isEqualTo(1);
    }

    @Test
    void retriesAnOverloadedModelInPlace() {
        queue("model-a", 503, "{\"error\":{\"status\":\"UNAVAILABLE\"}}");
        queue("model-a", 200, ok(MATCH));
        assertThat(client.generate("m", "s", "p", MatchAnalysis.class).model()).isEqualTo("model-a");
        assertThat(seen).hasSize(2);
    }

    @Test
    void fallsBackOnDailyQuotaAndRemembersTheExhaustedModel() {
        queue("model-a", 429, "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\",\"details\":[{\"quotaId\":\"GenerateRequestsPerDayPerProjectPerModel\"}]}}");
        queue("model-b", 200, ok(MATCH));
        queue("model-b", 200, ok(MATCH));

        assertThat(client.generate("m", "s", "p", MatchAnalysis.class).model()).isEqualTo("model-b");
        assertThat(client.generate("m", "s", "p", MatchAnalysis.class).model()).isEqualTo("model-b");
        // The second call went straight to model-b: no request is spent re-asking model-a.
        assertThat(seen.stream().filter(s -> s.path().contains("model-a")).count()).isEqualTo(1);
        assertThat(count("model-a", "quota")).isEqualTo(1);
    }

    @Test
    void allModelsOutOfDailyQuotaSaysSoPlainly() {
        String daily = "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\",\"message\":\"PerDay\"}}";
        queue("model-a", 429, daily);
        queue("model-b", 429, daily);
        assertThatThrownBy(() -> client.generate("m", "s", "p", MatchAnalysis.class))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("used up for today");
        // Now known to be exhausted: refused without any network call.
        int before = seen.size();
        assertThatThrownBy(() -> client.generate("m", "s", "p", MatchAnalysis.class)).isInstanceOf(ApiException.class);
        assertThat(seen).hasSize(before);
    }

    @Test
    void anEmptyOrBlockedCandidateIsUnreadableNotACrash() {
        queue("model-a", 200, "{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}");
        assertThatThrownBy(() -> client.generate("m", "s", "p", MatchAnalysis.class))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("unreadable");
        assertThat(count("model-a", "unreadable")).isEqualTo(1);
    }

    @Test
    void noApiKeyMeansDisabledWithoutAnyRequest() {
        var props = new AppProperties("x", List.of(), new AppProperties.Ai("", List.of("model-a"), 25, 6, "http://127.0.0.1:1"),
                new AppProperties.Jobs("", "", "ca", 180), new AppProperties.Mail(false, "x"));
        var disabled = new GeminiClient(props, mapper, Clock.systemUTC(), metrics);
        assertThatThrownBy(() -> disabled.generate("m", "s", "p", MatchAnalysis.class))
                .isInstanceOf(ApiException.class).hasMessageContaining("turned off");
        assertThat(seen).isEmpty();
    }
}
