package com.applymate.dto;

import java.util.List;
import java.util.Map;

/**
 * Aggregated analytics for the current user's job applications.
 * Rates are null, not 0, when there is nothing to divide by.
 */
public record AnalyticsSummaryResponse(
        long totalApplications,
        Map<String, Long> byStatus,
        /** The last 12 months, oldest first, including months with no applications. */
        List<MonthlyCount> applicationsByMonth,
        Integer responseRatePct,
        Integer interviewRatePct,
        Integer offerRatePct,
        /** Average days from applying to the first employer response. */
        Double avgDaysToResponse
) {
    public record MonthlyCount(String month, long count) {}
}
