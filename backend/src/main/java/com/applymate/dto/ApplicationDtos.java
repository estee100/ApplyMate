package com.applymate.dto;

import com.applymate.model.ApplicationStatus;
import com.applymate.model.CommunicationDirection;
import com.applymate.model.CommunicationKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/** Request and response shapes for application tracking. */
public final class ApplicationDtos {

    private ApplicationDtos() {}

    /**
     * Track a job: either {@code jobId} for a job already in the app, or
     * {@code companyName} + {@code jobTitle} for one applied to somewhere else.
     */
    public record CreateApplicationRequest(
            Long jobId,
            @Size(max = 500) String jobTitle,
            @Size(max = 500) String companyName,
            @Size(max = 500) String location,
            @Size(max = 2048) String jobPostingUrl,
            ApplicationStatus status,
            LocalDate dateApplied,
            @Size(max = 10000) String notes,
            @Size(max = 255) String nextAction,
            LocalDate nextActionDate
    ) {}

    /**
     * Partial update; null fields are left alone. A {@code note} is only accepted
     * together with a status change, because it is stored on the status event.
     * {@code clearNextAction} removes the next step (null cannot, since null means "unchanged").
     */
    public record UpdateApplicationRequest(
            ApplicationStatus status,
            @Size(max = 1000) String note,
            @Size(max = 500) String jobTitle,
            @Size(max = 500) String companyName,
            @Size(max = 500) String location,
            @Size(max = 2048) String jobPostingUrl,
            LocalDate dateApplied,
            @Size(max = 10000) String notes,
            @Size(max = 255) String nextAction,
            LocalDate nextActionDate,
            Boolean clearNextAction
    ) {}

    public record ApplicationResponse(
            Long id,
            Long jobId,
            String jobTitle,
            String companyName,
            String companyLogoUrl,
            String location,
            String jobPostingUrl,
            ApplicationStatus status,
            LocalDate dateApplied,
            Instant statusUpdatedAt,
            String notes,
            String nextAction,
            LocalDate nextActionDate,
            boolean needsFollowUp,
            boolean manual,
            Instant createdAt
    ) {}

    public record EventResponse(
            Long id,
            ApplicationStatus fromStatus,
            ApplicationStatus toStatus,
            String note,
            Instant changedAt
    ) {}

    public record CommunicationRequest(
            @NotNull CommunicationKind kind,
            @NotNull CommunicationDirection direction,
            Instant occurredAt,
            @Size(max = 255) String contactName,
            @Size(max = 500) String subject,
            @Size(max = 10000) String summary
    ) {}

    public record CommunicationResponse(
            Long id,
            Long applicationId,
            CommunicationKind kind,
            CommunicationDirection direction,
            Instant occurredAt,
            String contactName,
            String subject,
            String summary
    ) {}

    public record StatsResponse(
            long total,
            Map<String, Long> byStatus,
            long active,
            long offers,
            long needsFollowUp
    ) {}
}
