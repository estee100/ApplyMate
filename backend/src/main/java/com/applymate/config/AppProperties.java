package com.applymate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/** Everything under {@code app.*} in application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("http://localhost:3000") String frontendUrl,
        @DefaultValue({"http://localhost:3000", "http://127.0.0.1:3000"}) List<String> corsAllowedOrigins,
        @DefaultValue Ai ai,
        @DefaultValue Jobs jobs,
        @DefaultValue Mail mail
) {
    public record Ai(
            @DefaultValue("") String apiKey,
            /** Tried in order; a model out of quota is skipped for a while. */
            @DefaultValue({"gemini-flash-latest", "gemini-flash-lite-latest"}) List<String> models,
            /** Per-user cap so one person cannot burn the shared free-tier quota. */
            @DefaultValue("25") int dailyLimitPerUser,
            @DefaultValue("6") int perMinuteLimitPerUser
    ) {
        public boolean enabled() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Jobs(
            @DefaultValue("") String adzunaAppId,
            @DefaultValue("") String adzunaAppKey,
            /** Adzuna country code: ca, us, gb, au... */
            @DefaultValue("ca") String country,
            @DefaultValue("180") int feedCacheMinutes
    ) {
        public boolean adzunaEnabled() {
            return !adzunaAppId.isBlank() && !adzunaAppKey.isBlank();
        }
    }

    public record Mail(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("ApplyMate <no-reply@applymate.local>") String from
    ) {}
}
