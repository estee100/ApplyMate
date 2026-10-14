package com.applymate.dto;

import com.applymate.ai.Schemas.ResumeContent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ProfileDtos {

    private ProfileDtos() {}

    public record ResumeResponse(Long id, String fileName, String contentType, int characters, boolean active,
                                 boolean hasMaster, Instant uploadedAt) {}

    public record SkillProfileResponse(
            List<String> skills,
            List<String> jobTitles,
            List<String> domains,
            List<String> locations,
            String seniority,
            BigDecimal yearsExperience,
            String summary,
            String modelUsed,
            Instant generatedAt
    ) {}

    public record SkillProfileUpdate(
            @Size(max = 60) List<@Size(max = 80) String> skills,
            @Size(max = 10) List<@Size(max = 120) String> jobTitles,
            @Size(max = 20) List<@Size(max = 120) String> domains,
            @Size(max = 10) List<@Size(max = 120) String> locations,
            @Size(max = 60) String seniority,
            BigDecimal yearsExperience,
            @Size(max = 2000) String summary
    ) {}

    public record AiUsageResponse(boolean enabled, int usedToday, int dailyLimit) {}

    public record ProfileResponse(
            String email,
            String name,
            String preferredLocation,
            boolean includeRemote,
            boolean emailReminders,
            ResumeResponse resume,
            SkillProfileResponse skillProfile,
            AiUsageResponse ai,
            Instant memberSince
    ) {}

    public record ProfileUpdate(
            @Size(max = 120) String name,
            @Size(max = 255) String preferredLocation,
            Boolean includeRemote,
            Boolean emailReminders
    ) {}

    /** {@code warning} explains a partial success, e.g. the CV saved but skill extraction hit the quota. */
    public record UploadResponse(ResumeResponse resume, SkillProfileResponse skillProfile, String warning) {}

    public record MasterResumeResponse(ResumeContent content, boolean saved, long version) {}
}
