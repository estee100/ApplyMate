package com.applymate.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class Text {

    private Text() {}

    /** Trimmed, cut to {@code max} characters; null when blank. */
    public static String clip(String value, int max) {
        if (value == null) return null;
        String trimmed = value.strip();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max).strip();
    }

    /** Like {@link #clip} but never null, for JSON fields the frontend always expects. */
    public static String clipOrEmpty(String value, int max) {
        String clipped = clip(value, max);
        return clipped == null ? "" : clipped;
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Trimmed, non-blank, case-insensitively unique entries, at most {@code maxItems} of {@code maxLen}. */
    public static List<String> cleanList(List<String> values, int maxItems, int maxLen) {
        if (values == null) return new ArrayList<>();
        var seen = new LinkedHashSet<String>();
        var out = new ArrayList<String>();
        for (String value : values) {
            String clipped = clip(value, maxLen);
            if (clipped != null && seen.add(clipped.toLowerCase(Locale.ROOT))) {
                out.add(clipped);
                if (out.size() >= maxItems) break;
            }
        }
        return out;
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Lowercase, single-spaced, for comparing titles and company names. */
    public static String normalizeKey(String value) {
        return value == null ? "" : String.join(" ", value.toLowerCase(Locale.ROOT).trim().split("\\s+"));
    }
}
