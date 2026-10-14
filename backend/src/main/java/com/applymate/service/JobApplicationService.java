package com.applymate.service;

import com.applymate.dto.ApplicationDtos.*;
import com.applymate.error.ApiException;
import com.applymate.error.Versions;
import com.applymate.model.*;
import com.applymate.repository.*;
import com.applymate.util.Text;
import com.applymate.util.Urls;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Application tracking. Rules:
 * <ul>
 *   <li>Only a status change writes an {@link ApplicationEvent}; a note without one is rejected,
 *       so the event history stays a pure record of transitions.</li>
 *   <li>Another user's application is a 404, never a 403, so its existence is not confirmed.</li>
 *   <li>An open application needs following up once its next-step date has passed, or after
 *       {@link #FOLLOW_UP_AFTER} without a status change when no next step is set.</li>
 * </ul>
 */
@Service
public class JobApplicationService {

    static final Duration FOLLOW_UP_AFTER = Duration.ofDays(14);

    private final JobApplicationRepository applications;
    private final ApplicationEventRepository events;
    private final CommunicationRepository communications;
    private final JobRepository jobs;
    private final ResumeRepository resumes;
    private final CurrentUser currentUser;
    private final Clock clock;

    public JobApplicationService(JobApplicationRepository applications, ApplicationEventRepository events,
                                 CommunicationRepository communications, JobRepository jobs,
                                 ResumeRepository resumes, CurrentUser currentUser, Clock clock) {
        this.applications = applications;
        this.events = events;
        this.communications = communications;
        this.jobs = jobs;
        this.resumes = resumes;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // --- Applications ------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ApplicationResponse> list(ApplicationStatus status) {
        Long userId = currentUser.id();
        var rows = status == null ? applications.findForUser(userId) : applications.findForUserAndStatus(userId, status);
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(Long id) {
        return toResponse(owned(id));
    }

    @Transactional
    public ApplicationResponse create(CreateApplicationRequest req) {
        Long userId = currentUser.id();
        JobApplication app = new JobApplication();
        app.setUserId(userId);

        if (req.jobId() != null) {
            Job job = jobs.findById(req.jobId()).orElseThrow(() -> ApiException.notFound("Job"));
            if (applications.findForUserAndJob(userId, job.getId()).isPresent()) {
                throw ApiException.conflict("You are already tracking this job.");
            }
            app.setJob(job);
            app.setJobTitle(job.getTitle());
            app.setCompanyName(job.getCompany());
            app.setLocation(job.getLocation());
            app.setJobPostingUrl(job.getUrl());
            // Record which CV was current when they applied.
            resumes.findFirstByUserIdAndActiveTrueOrderByUploadedAtDesc(userId).ifPresent(r -> app.setResumeId(r.getId()));
        } else {
            String company = Text.clip(req.companyName(), 500);
            String title = Text.clip(req.jobTitle(), 500);
            if (company == null || title == null) {
                throw ApiException.badRequest("Company and position are required.");
            }
            app.setCompanyName(company);
            app.setJobTitle(title);
            app.setLocation(Text.clip(req.location(), 500));
            app.setJobPostingUrl(checkedUrl(req.jobPostingUrl()));
        }

        ApplicationStatus status = req.status() == null ? ApplicationStatus.APPLIED : req.status();
        app.setStatus(status);
        app.setDateApplied(req.dateApplied() != null ? req.dateApplied() : LocalDate.now(clock));
        app.setStatusUpdatedAt(Instant.now(clock));
        app.setNotes(Text.clip(req.notes(), 10000));
        app.setNextAction(Text.clip(req.nextAction(), 255));
        app.setNextActionDate(req.nextActionDate());

        try {
            applications.saveAndFlush(app);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent "Mark as applied" clicks: the partial unique index catches the second.
            throw ApiException.conflict("You are already tracking this job.");
        }
        events.save(new ApplicationEvent(app.getId(), null, status, null));
        return toResponse(app);
    }

    @Transactional
    public ApplicationResponse update(Long id, UpdateApplicationRequest req) {
        JobApplication app = owned(id);
        Versions.check(req.version(), app.getVersion());

        boolean statusChanges = req.status() != null && req.status() != app.getStatus();
        String note = Text.clip(req.note(), 1000);
        if (note != null && !statusChanges) {
            throw ApiException.unprocessable(
                    "A note is saved with a status change. To record an email or call, log a communication instead.");
        }
        if (statusChanges) {
            events.save(new ApplicationEvent(app.getId(), app.getStatus(), req.status(), note));
            app.setStatus(req.status());
            app.setStatusUpdatedAt(Instant.now(clock));
        }

        // Hand-logged applications own their details; tracked jobs take them from the listing.
        if (app.getJob() == null) {
            if (req.jobTitle() != null) app.setJobTitle(required(req.jobTitle(), "Position"));
            if (req.companyName() != null) app.setCompanyName(required(req.companyName(), "Company"));
            if (req.location() != null) app.setLocation(Text.clip(req.location(), 500));
            if (req.jobPostingUrl() != null) app.setJobPostingUrl(checkedUrl(req.jobPostingUrl()));
        }
        if (req.dateApplied() != null) app.setDateApplied(req.dateApplied());
        if (req.notes() != null) app.setNotes(Text.clip(req.notes(), 10000));
        if (Boolean.TRUE.equals(req.clearNextAction())) {
            app.setNextAction(null);
            app.setNextActionDate(null);
        } else {
            if (req.nextAction() != null) app.setNextAction(Text.clip(req.nextAction(), 255));
            if (req.nextActionDate() != null) app.setNextActionDate(req.nextActionDate());
        }
        // Flush now so the bumped version is in the response (and a lost race fails here).
        applications.saveAndFlush(app);
        return toResponse(app);
    }

    @Transactional
    public void delete(Long id) {
        applications.delete(owned(id));
    }

    @Transactional(readOnly = true)
    public List<EventResponse> events(Long id) {
        owned(id);
        return events.findByApplicationIdOrderByChangedAtAscIdAsc(id).stream()
                .map(e -> new EventResponse(e.getId(), e.getFromStatus(), e.getToStatus(), e.getNote(), e.getChangedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public StatsResponse stats() {
        var all = applications.findForUser(currentUser.id());
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ApplicationStatus s : ApplicationStatus.values()) byStatus.put(s.name(), 0L);
        long active = 0, followUp = 0;
        for (JobApplication app : all) {
            byStatus.merge(app.getStatus().name(), 1L, Long::sum);
            if (app.getStatus().isOpen()) active++;
            if (needsFollowUp(app)) followUp++;
        }
        // Rates live in AnalyticsService, which reads the event history; one definition only.
        return new StatsResponse(all.size(), byStatus, active, byStatus.get("OFFER"), followUp);
    }

    // --- Communications ----------------------------------------------------

    @Transactional(readOnly = true)
    public List<CommunicationResponse> communications(Long applicationId) {
        owned(applicationId);
        return communications.findByApplicationIdOrderByOccurredAtDescIdDesc(applicationId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public CommunicationResponse addCommunication(Long applicationId, CommunicationRequest req) {
        owned(applicationId);
        Communication c = new Communication();
        c.setApplicationId(applicationId);
        applyCommunication(c, req);
        return toResponse(communications.save(c));
    }

    @Transactional
    public CommunicationResponse updateCommunication(Long id, CommunicationRequest req) {
        Communication c = ownedCommunication(id);
        applyCommunication(c, req);
        return toResponse(communications.save(c));
    }

    @Transactional
    public void deleteCommunication(Long id) {
        communications.delete(ownedCommunication(id));
    }

    // --- Helpers -----------------------------------------------------------

    /** Package-visible for the reminder job and the dashboard. */
    boolean needsFollowUp(JobApplication app) {
        if (!app.getStatus().isOpen()) return false;
        if (app.getNextActionDate() != null) {
            return app.getNextActionDate().isBefore(LocalDate.now(clock));
        }
        return app.getStatusUpdatedAt().isBefore(Instant.now(clock).minus(FOLLOW_UP_AFTER));
    }

    JobApplication owned(Long id) {
        return applications.findOneForUser(id, currentUser.id())
                .orElseThrow(() -> ApiException.notFound("Application"));
    }

    private Communication ownedCommunication(Long id) {
        Communication c = communications.findById(id).orElseThrow(() -> ApiException.notFound("Communication"));
        // Ownership is checked through the application, and a miss is the same 404.
        applications.findOneForUser(c.getApplicationId(), currentUser.id())
                .orElseThrow(() -> ApiException.notFound("Communication"));
        return c;
    }

    private void applyCommunication(Communication c, CommunicationRequest req) {
        c.setKind(req.kind());
        c.setDirection(req.direction());
        c.setOccurredAt(req.occurredAt() != null ? req.occurredAt() : Instant.now(clock));
        c.setContactName(Text.clip(req.contactName(), 255));
        c.setSubject(Text.clip(req.subject(), 500));
        c.setSummary(Text.clip(req.summary(), 10000));
    }

    private static String required(String value, String field) {
        String clipped = Text.clip(value, 500);
        if (clipped == null) throw ApiException.badRequest(field + " cannot be empty.");
        return clipped;
    }

    private static String checkedUrl(String raw) {
        if (Text.isBlank(raw)) return null;
        String safe = Urls.safeHttp(raw);
        if (safe == null) throw ApiException.badRequest("The job link must start with http:// or https://");
        return safe;
    }

    ApplicationResponse toResponse(JobApplication a) {
        Job job = a.getJob();
        return new ApplicationResponse(
                a.getId(),
                job == null ? null : job.getId(),
                a.getJobTitle(),
                a.getCompanyName(),
                job == null ? null : job.getCompanyLogoUrl(),
                a.getLocation(),
                a.getJobPostingUrl(),
                a.getStatus(),
                a.getDateApplied(),
                a.getStatusUpdatedAt(),
                a.getNotes(),
                a.getNextAction(),
                a.getNextActionDate(),
                needsFollowUp(a),
                job == null,
                a.getCreatedAt(),
                a.getVersion()
        );
    }

    private CommunicationResponse toResponse(Communication c) {
        return new CommunicationResponse(c.getId(), c.getApplicationId(), c.getKind(), c.getDirection(),
                c.getOccurredAt(), c.getContactName(), c.getSubject(), c.getSummary());
    }
}
