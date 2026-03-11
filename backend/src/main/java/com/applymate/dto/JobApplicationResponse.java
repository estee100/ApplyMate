package com.applymate.dto;

import com.applymate.model.ApplicationStatus;

import java.time.LocalDate;

public record JobApplicationResponse(
        Long id,
        String jobTitle,
        String companyName,
        String location,
        String jobPostingUrl,
        ApplicationStatus status,
        LocalDate dateApplied,
        String notes
) {}
