package com.applymate.dto;

import java.util.Map;

/**
 * Aggregated analytics for the current user's job applications.
 */
public record AnalyticsSummaryResponse(
        long totalApplications,
        Map<String, Long> byStatus,
        java.util.List<MonthlyCount> applicationsByMonth
) {
    public record MonthlyCount(String month, long count) {}
}
