package com.applymate.service;

import com.applymate.dto.AnalyticsSummaryResponse;
import com.applymate.model.ApplicationStatus;
import com.applymate.model.User;
import com.applymate.repository.JobApplicationRepository;
import com.applymate.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {

    private final JobApplicationRepository jobApplicationRepository;
    private final UserRepository userRepository;

    public AnalyticsService(
            JobApplicationRepository jobApplicationRepository,
            UserRepository userRepository
    ) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary() {
        Long userId = currentUserId();
        long total = jobApplicationRepository.countForUser(userId);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ApplicationStatus s : ApplicationStatus.values()) {
            byStatus.put(s.name(), jobApplicationRepository.countForUserAndStatus(userId, s));
        }

        List<AnalyticsSummaryResponse.MonthlyCount> byMonth = new ArrayList<>();
        for (Object[] row : jobApplicationRepository.countByMonthForUser(userId)) {
            String month = row[0] != null ? row[0].toString() : "";
            long count = row[1] instanceof Number n ? n.longValue() : 0L;
            byMonth.add(new AnalyticsSummaryResponse.MonthlyCount(month, count));
        }

        return new AnalyticsSummaryResponse(total, byStatus, byMonth);
    }

    private Long currentUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }
}
