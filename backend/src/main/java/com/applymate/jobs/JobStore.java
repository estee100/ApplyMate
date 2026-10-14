package com.applymate.jobs;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

/**
 * Upserts listings into the shared {@code jobs} table. One atomic
 * {@code INSERT ... ON CONFLICT} per job, so two users searching at once can never
 * race each other into a unique-constraint failure.
 */
@Component
public class JobStore {

    private final JdbcTemplate jdbc;

    public JobStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns job ids in the same order as the input. */
    public List<Long> upsert(List<NormalizedJob> jobs) {
        List<Long> ids = new ArrayList<>(jobs.size());
        for (NormalizedJob job : jobs) ids.add(upsert(job));
        return ids;
    }

    public Long upsert(NormalizedJob job) {
        return jdbc.queryForObject("""
                INSERT INTO jobs (source, external_id, title, company, company_logo_url, location, salary, url,
                                  publisher, job_type, posted_date, description)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source, external_id) DO UPDATE SET
                    title = EXCLUDED.title,
                    company = EXCLUDED.company,
                    company_logo_url = COALESCE(EXCLUDED.company_logo_url, jobs.company_logo_url),
                    location = EXCLUDED.location,
                    salary = EXCLUDED.salary,
                    url = COALESCE(EXCLUDED.url, jobs.url),
                    publisher = EXCLUDED.publisher,
                    job_type = COALESCE(EXCLUDED.job_type, jobs.job_type),
                    posted_date = COALESCE(EXCLUDED.posted_date, jobs.posted_date),
                    description = COALESCE(EXCLUDED.description, jobs.description),
                    updated_at = NOW()
                RETURNING id""",
                Long.class,
                job.source().name(), job.externalId(), job.title(), job.company(), job.companyLogoUrl(),
                job.location(), job.salary(), job.url(), job.publisher(),
                job.jobType() == null ? null : job.jobType().name(),
                job.postedDate() == null ? null : Date.valueOf(job.postedDate()),
                job.description());
    }

    /** Fallback when every source is down: stored jobs whose title or company matches. */
    public List<Long> searchStored(String q, int limit) {
        if (q == null || q.isBlank()) {
            return jdbc.queryForList("SELECT id FROM jobs WHERE source <> 'PASTED' ORDER BY posted_date DESC NULLS LAST LIMIT ?",
                    Long.class, limit);
        }
        String like = "%" + q.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.queryForList("""
                SELECT id FROM jobs
                WHERE source <> 'PASTED' AND (LOWER(title) LIKE ? OR LOWER(company) LIKE ?)
                ORDER BY posted_date DESC NULLS LAST LIMIT ?""", Long.class, like, like, limit);
    }
}
