package com.applymate.util;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

/** URL checks for anything rendered as an href. */
public final class Urls {

    private Urls() {}

    /**
     * The URL if it is absolute http(s), otherwise null. A {@code javascript:} URL
     * stored as an apply link would run when clicked.
     */
    public static String safeHttp(String value) {
        return safe(value, false);
    }

    /** Like {@link #safeHttp} but https only, used for images loaded on every page view. */
    public static String safeHttps(String value) {
        return safe(value, true);
    }

    private static String safe(String value, boolean httpsOnly) {
        if (value == null) return null;
        String trimmed = value.strip();
        if (trimmed.isEmpty() || trimmed.length() > 2048) return null;
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            boolean allowed = httpsOnly ? scheme.equals("https") : (scheme.equals("http") || scheme.equals("https"));
            if (!allowed || uri.getHost() == null || uri.getHost().isBlank()) return null;
            return trimmed;
        } catch (Exception e) {
            return null;
        }
    }

    private static final Map<String, String> KNOWN_BOARDS = Map.ofEntries(
            Map.entry("linkedin.", "LinkedIn"),
            Map.entry("indeed.", "Indeed"),
            Map.entry("glassdoor.", "Glassdoor"),
            Map.entry("ziprecruiter.", "ZipRecruiter"),
            Map.entry("wellfound.com", "Wellfound"),
            Map.entry("greenhouse.io", "Greenhouse"),
            Map.entry("lever.co", "Lever"),
            Map.entry("ashbyhq.com", "Ashby"),
            Map.entry("workable.com", "Workable"),
            Map.entry("smartrecruiters.", "SmartRecruiters"),
            Map.entry("bamboohr.", "BambooHR"),
            Map.entry("myworkdayjobs.", "Workday"),
            Map.entry("adzuna.", "Adzuna"),
            Map.entry("himalayas.app", "Himalayas")
    );

    /** A readable name for where an apply link goes ("LinkedIn"), falling back to the domain. */
    public static String publisherFor(String url) {
        if (url == null) return null;
        try {
            String host = URI.create(url).getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.ROOT);
            for (var entry : KNOWN_BOARDS.entrySet()) {
                if (host.contains(entry.getKey())) return entry.getValue();
            }
            return Text.clip(host.startsWith("www.") ? host.substring(4) : host, 120);
        } catch (Exception e) {
            return null;
        }
    }
}
