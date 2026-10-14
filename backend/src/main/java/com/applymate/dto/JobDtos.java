package com.applymate.dto;

import com.applymate.dto.ApplicationDtos.ApplicationResponse;
import com.applymate.model.ApplicationStatus;
import com.applymate.model.DocumentKind;
import com.applymate.model.JobSource;
import com.applymate.model.JobType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class JobDtos {

    private JobDtos() {}

    /** Card figures, read from the cached analysis: rendering a card never calls the model. */
    public record MatchSummary(int percentage, int metCount, int missingCount) {}

    public record JobCard(
            Long id,
            String title,
            String company,
            String companyLogoUrl,
            String location,
            String salary,
            String url,
            String publisher,
            JobSource source,
            JobType jobType,
            LocalDate postedDate,
            boolean saved,
            Long applicationId,
            ApplicationStatus applicationStatus,
            MatchSummary match
    ) {}

    public record MatchResponse(
            int matchPercentage,
            List<String> requirementsMet,
            List<String> requirementsMissing,
            String rationale,
            String modelUsed,
            Instant generatedAt
    ) {}

    public record DocumentSummary(Long id, DocumentKind kind, String modelUsed, boolean edited,
                                  Instant createdAt, Instant updatedAt) {}

    public record JobDetail(
            JobCard job,
            String description,
            /** False for jobs without an advert, where analysis and generation are refused. */
            boolean analyzable,
            MatchResponse match,
            ApplicationResponse application,
            List<DocumentSummary> documents,
            boolean hasResume
    ) {}

    public record FeedResponse(List<JobCard> jobs, List<String> sources, String notice, boolean hasMore, int page) {}

    public record PasteLinkRequest(@NotBlank @Size(max = 2048) String url) {}

    public record PasteTextRequest(
            @NotBlank @Size(max = 500) String title,
            @NotBlank @Size(max = 255) String company,
            @Size(max = 255) String location,
            @Size(max = 2048) String url,
            @NotBlank @Size(min = 50, max = 40000) String description
    ) {}
}
