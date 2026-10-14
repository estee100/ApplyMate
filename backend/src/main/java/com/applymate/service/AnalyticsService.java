package com.applymate.service;

import com.applymate.dto.AnalyticsSummaryResponse;
import com.applymate.dto.AnalyticsSummaryResponse.MonthlyCount;
import com.applymate.model.ApplicationEvent;
import com.applymate.model.ApplicationStatus;
import com.applymate.model.JobApplication;
import com.applymate.repository.ApplicationEventRepository;
import com.applymate.repository.JobApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Pipeline analytics, computed in memory from the user's applications and their
 * event history. A personal tracker holds hundreds of rows at most, so this is
 * cheaper and clearer than a set of aggregate queries.
 */
@Service
public class AnalyticsService {

    private static final int MONTHS = 12;

    private final JobApplicationRepository applications;
    private final ApplicationEventRepository events;
    private final CurrentUser currentUser;
    private final Clock clock;

    public AnalyticsService(JobApplicationRepository applications, ApplicationEventRepository events,
                            CurrentUser currentUser, Clock clock) {
        this.applications = applications;
        this.events = events;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary() {
        Long userId = currentUser.id();
        List<JobApplication> apps = applications.findForUser(userId);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ApplicationStatus s : ApplicationStatus.values()) byStatus.put(s.name(), 0L);
        apps.forEach(a -> byStatus.merge(a.getStatus().name(), 1L, Long::sum));

        // Which stages has each application ever reached? Current status alone undercounts:
        // an application interviewed and then rejected still had an interview.
        Map<Long, Set<ApplicationStatus>> reached = new HashMap<>();
        Map<Long, LocalDate> firstResponse = new HashMap<>();
        ZoneId zone = clock.getZone();
        for (ApplicationEvent e : events.findForUser(userId)) {
            reached.computeIfAbsent(e.getApplicationId(), k -> EnumSet.noneOf(ApplicationStatus.class)).add(e.getToStatus());
            if (e.getToStatus().isResponse()) {
                firstResponse.putIfAbsent(e.getApplicationId(), e.getChangedAt().atZone(zone).toLocalDate());
            }
        }

        long considered = 0, responded = 0, interviewed = 0, offered = 0;
        double totalDays = 0;
        int timed = 0;
        for (JobApplication a : apps) {
            Set<ApplicationStatus> stages = reached.getOrDefault(a.getId(), EnumSet.noneOf(ApplicationStatus.class));
            stages.add(a.getStatus());
            if (a.getStatus() == ApplicationStatus.WITHDRAWN && stages.stream().noneMatch(ApplicationStatus::isResponse)) {
                continue; // withdrawn before the employer replied says nothing about response rate
            }
            considered++;
            if (stages.stream().anyMatch(ApplicationStatus::isResponse)) responded++;
            if (stages.contains(ApplicationStatus.INTERVIEWING) || stages.contains(ApplicationStatus.OFFER)) interviewed++;
            if (stages.contains(ApplicationStatus.OFFER)) offered++;
            LocalDate first = firstResponse.get(a.getId());
            if (first != null && !first.isBefore(a.getDateApplied())) {
                totalDays += ChronoUnit.DAYS.between(a.getDateApplied(), first);
                timed++;
            }
        }

        return new AnalyticsSummaryResponse(
                apps.size(),
                byStatus,
                byMonth(apps),
                pct(responded, considered),
                pct(interviewed, considered),
                pct(offered, considered),
                timed == 0 ? null : Math.round(totalDays / timed * 10) / 10.0
        );
    }

    private List<MonthlyCount> byMonth(List<JobApplication> apps) {
        YearMonth current = YearMonth.now(clock);
        Map<YearMonth, Long> counts = new TreeMap<>();
        for (int i = MONTHS - 1; i >= 0; i--) counts.put(current.minusMonths(i), 0L);
        for (JobApplication a : apps) {
            YearMonth month = YearMonth.from(a.getDateApplied());
            counts.computeIfPresent(month, (k, v) -> v + 1);
        }
        return counts.entrySet().stream().map(e -> new MonthlyCount(e.getKey().toString(), e.getValue())).toList();
    }

    private static Integer pct(long part, long whole) {
        return whole == 0 ? null : (int) Math.round(100.0 * part / whole);
    }
}
