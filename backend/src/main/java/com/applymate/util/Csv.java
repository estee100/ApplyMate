package com.applymate.util;

import java.util.ArrayList;
import java.util.List;

/** RFC 4180 CSV: quoted fields, escaped quotes, and newlines inside quotes. */
public final class Csv {

    private Csv() {}

    /** Parses a whole document into rows of fields. Tolerates CRLF or LF and a trailing newline. */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int i = 0;
        if (text.startsWith("﻿")) i = 1; // Excel's UTF-8 byte order mark
        for (; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"' && field.isEmpty()) {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString());
                field.setLength(0);
                if (!(row.size() == 1 && row.getFirst().isEmpty())) rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    /**
     * One field, quoted when needed. A value starting with = + - @ (or a tab or CR) is
     * prefixed with an apostrophe: spreadsheets would otherwise run it as a formula, and
     * a job title like {@code =HYPERLINK("http://evil",...)} is CSV injection.
     */
    public static String field(String value) {
        if (value == null) return "";
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        boolean quote = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
        return quote ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    public static String row(List<String> values) {
        return String.join(",", values.stream().map(Csv::field).toList()) + "\r\n";
    }
}
