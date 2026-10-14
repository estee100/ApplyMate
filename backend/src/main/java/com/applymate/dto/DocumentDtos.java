package com.applymate.dto;

import com.applymate.dto.ApplicationDtos.ApplicationResponse;
import com.applymate.model.DocumentKind;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DocumentDtos {

    private DocumentDtos() {}

    /** Optional steering, e.g. "emphasise my backend work". */
    public record GenerateRequest(@Size(max = 500) String instructions) {}

    public record DocumentResponse(
            Long id,
            DocumentKind kind,
            Long jobId,
            String jobTitle,
            String company,
            /** What the editor shows: the user's edit if any, else the original. */
            JsonNode content,
            /** The original output, kept so "reset" always works. */
            JsonNode original,
            boolean edited,
            String modelUsed,
            String authorName,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {}

    public record SaveDocumentRequest(@NotNull JsonNode content, Long version) {}

    /** One job in History: what was done on it, newest activity first. */
    public record HistoryEntry(
            Long jobId,
            String jobTitle,
            String company,
            String companyLogoUrl,
            String url,
            String publisher,
            ApplicationResponse application,
            List<JobDtos.DocumentSummary> documents,
            Instant lastActivity
    ) {}

    public record InterviewPrepResponse(JsonNode content, String modelUsed, Instant generatedAt) {}

    public record CalendarEvent(
            String id,
            /** APPLIED, NEXT_ACTION or STATUS_CHANGE. */
            String type,
            LocalDate date,
            String title,
            String detail,
            Long applicationId,
            boolean overdue
    ) {}
}
