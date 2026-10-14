package com.applymate.service;

import com.applymate.model.ApplicationStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Search across the user's own applications and jobs (pasted, saved, applied to or
 * documented), backed by the generated tsvector columns and their GIN indexes.
 *
 * <p>The query is built for search-as-you-type: each word becomes a prefix term
 * ({@code java:* & spri:*}), so "spri" already finds "Spring". Words are reduced to
 * letters and digits before they reach {@code to_tsquery}, whose own syntax
 * ({@code & | ! ( ) :}) would otherwise be injectable and could make it throw.
 */
@Service
public class SearchService {

    static final int LIMIT = 8;
    private static final String MARK_START = "\u0002";
    private static final String MARK_END = "\u0003";
    private static final String HEADLINE_OPTIONS =
            "StartSel=" + MARK_START + ", StopSel=" + MARK_END + ", MaxWords=18, MinWords=8, MaxFragments=1, FragmentDelimiter= … ";

    private final JdbcTemplate jdbc;
    private final CurrentUser currentUser;

    public SearchService(JdbcTemplate jdbc, CurrentUser currentUser) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    /** A highlighted fragment: the frontend renders the marked ranges in bold, never as HTML. */
    public record Snippet(String text, List<int[]> highlights) {}

    public record ApplicationHit(Long id, String jobTitle, String companyName, ApplicationStatus status, Snippet snippet) {}

    public record JobHit(Long id, String title, String company, String location, boolean saved, Snippet snippet) {}

    public record Results(String query, List<ApplicationHit> applications, List<JobHit> jobs) {}

    public Results search(String raw) {
        String tsquery = toPrefixQuery(raw);
        if (tsquery == null) return new Results(raw, List.of(), List.of());
        Long userId = currentUser.id();

        List<ApplicationHit> applications = jdbc.query("""
                SELECT a.id, a.job_title, a.company_name, a.status,
                       ts_headline('english', coalesce(a.notes, a.job_title || ' at ' || a.company_name), q, ?) AS snippet
                FROM job_applications a, to_tsquery('english', ?) q
                WHERE a.user_id = ? AND a.search_vector @@ q
                ORDER BY ts_rank_cd(a.search_vector, q) DESC, a.status_updated_at DESC
                LIMIT ?""",
                (rs, i) -> new ApplicationHit(rs.getLong("id"), rs.getString("job_title"), rs.getString("company_name"),
                        ApplicationStatus.valueOf(rs.getString("status")), snippet(rs.getString("snippet"))),
                HEADLINE_OPTIONS, tsquery, userId, LIMIT);

        // Only jobs this user has a relationship with: searching every listing anyone ever
        // fetched would leak other users' pasted jobs.
        List<JobHit> jobs = jdbc.query("""
                SELECT j.id, j.title, j.company, j.location,
                       EXISTS (SELECT 1 FROM saved_jobs s WHERE s.user_id = ? AND s.job_id = j.id) AS saved,
                       ts_headline('english', coalesce(left(j.description, 5000), j.title), q, ?) AS snippet
                FROM jobs j, to_tsquery('english', ?) q
                WHERE j.search_vector @@ q
                  AND j.id IN (
                      SELECT job_id FROM user_jobs WHERE user_id = ?
                      UNION SELECT job_id FROM saved_jobs WHERE user_id = ?
                      UNION SELECT job_id FROM generated_documents WHERE user_id = ?
                      UNION SELECT job_id FROM job_applications WHERE user_id = ? AND job_id IS NOT NULL)
                ORDER BY ts_rank_cd(j.search_vector, q) DESC, j.posted_date DESC NULLS LAST
                LIMIT ?""",
                (rs, i) -> new JobHit(rs.getLong("id"), rs.getString("title"), rs.getString("company"),
                        rs.getString("location"), rs.getBoolean("saved"), snippet(rs.getString("snippet"))),
                userId, HEADLINE_OPTIONS, tsquery, userId, userId, userId, userId, LIMIT);

        return new Results(raw, applications, jobs);
    }

    /** "Java  Spri" becomes "java:* & spri:*"; null when nothing searchable is left. */
    static String toPrefixQuery(String raw) {
        if (raw == null) return null;
        List<String> terms = new ArrayList<>();
        for (String word : raw.toLowerCase(Locale.ROOT).split("\\s+")) {
            String clean = word.replaceAll("[^\\p{L}\\p{N}]", "");
            if (!clean.isEmpty() && terms.size() < 8) terms.add(clean + ":*");
        }
        return terms.isEmpty() ? null : String.join(" & ", terms);
    }

    /** Turns the marker characters from ts_headline into plain text plus highlight ranges. */
    static Snippet snippet(String marked) {
        if (marked == null) return new Snippet("", List.of());
        StringBuilder text = new StringBuilder();
        List<int[]> ranges = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < marked.length(); i++) {
            char c = marked.charAt(i);
            if (c == MARK_START.charAt(0)) start = text.length();
            else if (c == MARK_END.charAt(0)) {
                if (start >= 0) ranges.add(new int[]{start, text.length()});
                start = -1;
            } else text.append(c == '\n' ? ' ' : c);
        }
        return new Snippet(text.toString(), ranges); // not stripped: that would shift the ranges
    }
}
