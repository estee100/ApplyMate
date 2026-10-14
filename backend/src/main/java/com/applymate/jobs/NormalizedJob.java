package com.applymate.jobs;

import com.applymate.model.JobSource;
import com.applymate.model.JobType;

import java.time.LocalDate;

/** A listing from any source, cleaned and ready to upsert into {@code jobs}. */
public record NormalizedJob(
        JobSource source,
        String externalId,
        String title,
        String company,
        String companyLogoUrl,
        String location,
        String salary,
        String url,
        String publisher,
        JobType jobType,
        LocalDate postedDate,
        String description
) {}
