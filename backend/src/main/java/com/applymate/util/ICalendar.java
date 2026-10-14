package com.applymate.util;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Builds an RFC 5545 iCalendar document: escaped text and lines folded at 75 octets. */
public final class ICalendar {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final StringBuilder out = new StringBuilder();

    public ICalendar(String name) {
        line("BEGIN:VCALENDAR");
        line("VERSION:2.0");
        line("PRODID:-//ApplyMate//Applications//EN");
        line("CALSCALE:GREGORIAN");
        line("METHOD:PUBLISH");
        line("X-WR-CALNAME:" + escape(name));
        // Ask subscribing clients to refresh hourly.
        line("REFRESH-INTERVAL;VALUE=DURATION:PT1H");
        line("X-PUBLISHED-TTL:PT1H");
    }

    /** An all-day event. The uid must be stable so clients update events instead of duplicating them. */
    public ICalendar allDayEvent(String uid, LocalDate day, String summary, String description, String url, Instant stamp) {
        line("BEGIN:VEVENT");
        line("UID:" + escape(uid));
        line("DTSTAMP:" + STAMP.format(stamp));
        line("DTSTART;VALUE=DATE:" + DATE.format(day));
        line("DTEND;VALUE=DATE:" + DATE.format(day.plusDays(1)));
        line("SUMMARY:" + escape(summary));
        if (description != null && !description.isBlank()) line("DESCRIPTION:" + escape(description));
        if (url != null) line("URL:" + url);
        line("END:VEVENT");
        return this;
    }

    public String build() {
        line("END:VCALENDAR");
        return out.toString();
    }

    /** TEXT escaping from RFC 5545 section 3.3.11. */
    static String escape(String text) {
        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
    }

    /** Lines longer than 75 octets are folded with CRLF + space, never splitting a UTF-8 character. */
    private void line(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 75) {
            out.append(content).append("\r\n");
            return;
        }
        int octets = 0;
        int limit = 75;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            int size = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > limit) {
                out.append(current).append("\r\n ");
                current.setLength(0);
                octets = 0;
                limit = 74; // continuation lines start with the folding space
            }
            current.appendCodePoint(cp);
            octets += size;
            i += Character.charCount(cp);
        }
        out.append(current).append("\r\n");
    }
}
