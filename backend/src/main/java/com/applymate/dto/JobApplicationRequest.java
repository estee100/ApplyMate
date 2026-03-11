package com.applymate.dto;

import com.applymate.model.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record JobApplicationRequest(
        @NotBlank String jobTitle,
        @NotBlank String companyName,
        String location,
        String jobPostingUrl,
        @NotNull ApplicationStatus status,
        @NotNull LocalDate dateApplied,
        String notes
) {}
