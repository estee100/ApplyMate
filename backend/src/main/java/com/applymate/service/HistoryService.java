package com.applymate.service;

import com.applymate.dto.DocumentDtos.CalendarEvent;
import com.applymate.dto.DocumentDtos.HistoryEntry;
import com.applymate.dto.JobDtos.DocumentSummary;
import com.applymate.model.ApplicationEvent;
import com.applymate.model.GeneratedDocument;
import com.applymate.model.Job;
import com.applymate.model.JobApplication;
import com.applymate.repository.ApplicationEventRepository;
import com.applymate.repository.GeneratedDocumentRepository;
import com.applymate.repository.JobApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * History and Calendar are views over the same tables as tracking, not copies:
 * every page shows the same state.
 */
@Service
public class HistoryService {

    private final JobApplicationRepository applications;
    private final ApplicationEventRepository events;
    private final GeneratedDocumentRepository documents;
    private final JobApplicationService applicationService;
    private final CurrentUser currentUser;
    private final Clock clock;

    public HistoryService(JobApplicationRepository applications, ApplicationEventRepository events,
                          GeneratedDocumentRepository documents, JobApplicationService applicationService,
                          CurrentUser currentUser, Clock clock) {
        this.applications = applications;
        this.events = events;
        this.documents = documents;
        this.applicationService = applicationService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * Every job applied to or prepared for, newest activity first. A job with both an
     * application and documents is one entry; a hand-logged application is its own.
     */
    @Transactional(readOnly = true)
    public List<HistoryEntry> history() {
        Long userId = currentUser.id();
        Map<String, Builder> byKey = new LinkedHashMap<>();

        for (JobApplication app : applications.findForUser(userId)) {
            String key = app.getJob() != null ? "job:" + app.getJob().getId() : "app:" + app.getId();
            Builder b = byKey.computeIfAbsent(key, k -> new Builder());
            b.app = app;
            b.job = app.getJob();
            b.touch(app.getStatusUpdatedAt());
            b.touch(app.getCreatedAt());
        }
        for (GeneratedDocument doc : documents.findAllForUser(userId)) {
            Builder b = byKey.computeIfAbsent("job:" + doc.getJob().getId(), k -> new Builder());
            b.job = doc.getJob();
            b.docs.add(new DocumentSummary(doc.getId(), doc.getKind(), doc.getModelUsed(), doc.getEditedContent() != null,
                    doc.getCreatedAt(), doc.getUpdatedAt()));
            b.touch(doc.getUpdatedAt());
        }

        return byKey.values().stream()
                .map(b -> {
                    Job job = b.job;
                    JobApplication app = b.app;
                    return new HistoryEntry(
                            job == null ? null : job.getId(),
                            job != null ? job.getTitle() : app.getJobTitle(),
                            job != null ? job.getCompany() : app.getCompanyName(),
                            job == null ? null : job.getCompanyLogoUrl(),
                            job != null ? job.getUrl() : app.getJobPostingUrl(),
                            job == null ? null : job.getPublisher(),
                            app == null ? null : applicationService.toResponse(app),
                            b.docs,
                            b.last);
                })
                .sorted(Comparator.comparing(HistoryEntry::lastActivity).reversed())
                .toList();
    }

    /** Dated events for the calendar: applied dates, next steps and status changes. */
    @Transactional(readOnly = true)
    public List<CalendarEvent> calendar(LocalDate from, LocalDate to) {
        Long userId = currentUser.id();
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        List<JobApplication> apps = applications.findForUser(userId);
        Map<Long, JobApplication> byId = new HashMap<>();
        apps.forEach(a -> byId.put(a.getId(), a));

        List<CalendarEvent> out = new ArrayList<>();
        for (JobApplication a : apps) {
            String label = a.getJobTitle() + " at " + a.getCompanyName();
            if (within(a.getDateApplied(), from, to)) {
                out.add(new CalendarEvent("applied-" + a.getId(), "APPLIED", a.getDateApplied(), "Applied: " + label,
                        null, a.getId(), false));
            }
            if (a.getNextActionDate() != null && within(a.getNextActionDate(), from, to)) {
                boolean overdue = a.getStatus().isOpen() && a.getNextActionDate().isBefore(today);
                out.add(new CalendarEvent("next-" + a.getId(), "NEXT_ACTION", a.getNextActionDate(),
                        (a.getNextAction() == null ? "Next step" : a.getNextAction()) + ": " + label,
                        null, a.getId(), overdue));
            }
        }
        for (ApplicationEvent e : events.findForUser(userId)) {
            if (e.getFromStatus() == null) continue; // creation is already the "applied" event
            LocalDate day = e.getChangedAt().atZone(zone).toLocalDate();
            JobApplication a = byId.get(e.getApplicationId());
            if (a == null || !within(day, from, to)) continue;
            out.add(new CalendarEvent("event-" + e.getId(), "STATUS_CHANGE", day,
                    a.getCompanyName() + ": " + e.getToStatus().name(), e.getNote(), a.getId(), false));
        }
        out.sort(Comparator.comparing(CalendarEvent::date).thenComparing(CalendarEvent::id));
        return out;
    }

    private static boolean within(LocalDate day, LocalDate from, LocalDate to) {
        return day != null && !day.isBefore(from) && !day.isAfter(to);
    }

    private static final class Builder {
        JobApplication app;
        Job job;
        final List<DocumentSummary> docs = new ArrayList<>();
        Instant last = Instant.EPOCH;

        void touch(Instant when) {
            if (when != null && when.isAfter(last)) last = when;
        }
    }
}
