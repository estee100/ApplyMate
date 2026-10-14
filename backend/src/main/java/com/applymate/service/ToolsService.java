package com.applymate.service;

import com.applymate.ai.AiService;
import com.applymate.ai.Schemas.EmailDraft;
import com.applymate.config.AppProperties;
import com.applymate.error.ApiException;
import com.applymate.model.*;
import com.applymate.repository.ApplicationEventRepository;
import com.applymate.repository.CommunicationRepository;
import com.applymate.repository.JobApplicationRepository;
import com.applymate.repository.UserRepository;
import com.applymate.util.Csv;
import com.applymate.util.ICalendar;
import com.applymate.util.Text;
import com.applymate.util.Urls;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Smaller features around applications: weekly progress, CSV import and export, the
 * private calendar feed, and AI email drafts.
 */
@Service
public class ToolsService {

    private static final SecureRandom RANDOM = new SecureRandom();
    static final int MAX_IMPORT_ROWS = 1000;

    private final JobApplicationRepository applications;
    private final ApplicationEventRepository events;
    private final CommunicationRepository communications;
    private final JobApplicationService applicationService;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final AiService ai;
    private final AppProperties properties;
    private final Clock clock;

    public ToolsService(JobApplicationRepository applications, ApplicationEventRepository events,
                        CommunicationRepository communications, JobApplicationService applicationService,
                        UserRepository users, CurrentUser currentUser, AiService ai, AppProperties properties, Clock clock) {
        this.applications = applications;
        this.events = events;
        this.communications = communications;
        this.applicationService = applicationService;
        this.users = users;
        this.currentUser = currentUser;
        this.ai = ai;
        this.properties = properties;
        this.clock = clock;
    }

    // --- Weekly goal --------------------------------------------------------------

    public record WeekCount(LocalDate weekStart, long count) {}

    public record Progress(int goal, long thisWeek, int streakWeeks, List<WeekCount> recentWeeks) {}

    /**
     * Applications per week (Monday start) against the user's goal. The streak counts
     * consecutive weeks that met the goal, ending last week, plus this week once met:
     * an unfinished week never breaks a streak.
     */
    @Transactional(readOnly = true)
    public Progress progress() {
        User user = currentUser.get();
        LocalDate thisMonday = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<LocalDate, Long> perWeek = new HashMap<>();
        for (JobApplication a : applications.findForUser(user.getId())) {
            perWeek.merge(a.getDateApplied().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), 1L, Long::sum);
        }
        int goal = user.getWeeklyGoal();
        long thisWeek = perWeek.getOrDefault(thisMonday, 0L);
        int streak = thisWeek >= goal ? 1 : 0;
        for (LocalDate week = thisMonday.minusWeeks(1); perWeek.getOrDefault(week, 0L) >= goal; week = week.minusWeeks(1)) {
            streak++;
        }
        List<WeekCount> recent = new ArrayList<>();
        for (int i = 7; i >= 0; i--) {
            LocalDate week = thisMonday.minusWeeks(i);
            recent.add(new WeekCount(week, perWeek.getOrDefault(week, 0L)));
        }
        return new Progress(goal, thisWeek, streak, recent);
    }

    // --- CSV ----------------------------------------------------------------------

    private static final List<String> HEADER = List.of("Company", "Position", "Status", "Date applied", "Location",
            "Link", "Next step", "Next step date", "Notes");

    @Transactional(readOnly = true)
    public String exportCsv() {
        StringBuilder out = new StringBuilder("﻿"); // so Excel opens it as UTF-8
        out.append(Csv.row(HEADER));
        for (JobApplication a : applications.findForUser(currentUser.id())) {
            out.append(Csv.row(Arrays.asList(a.getCompanyName(), a.getJobTitle(), a.getStatus().name(),
                    a.getDateApplied().toString(), a.getLocation(), a.getJobPostingUrl(), a.getNextAction(),
                    a.getNextActionDate() == null ? null : a.getNextActionDate().toString(), a.getNotes())));
        }
        return out.toString();
    }

    public record ImportError(int line, String reason) {}

    public record ImportResult(int imported, List<ImportError> skipped) {}

    /**
     * Import a spreadsheet of applications. Columns are found by header name (several
     * spellings accepted, any order), so an export from another tracker usually just works.
     * Each valid row becomes a hand-logged application; invalid rows are reported by line
     * and skipped rather than failing the whole file.
     */
    @Transactional
    public ImportResult importCsv(String text) {
        List<List<String>> rows = Csv.parse(text);
        if (rows.isEmpty()) throw ApiException.badRequest("That file is empty.");
        if (rows.size() - 1 > MAX_IMPORT_ROWS) throw ApiException.badRequest("Import at most " + MAX_IMPORT_ROWS + " rows at a time.");

        Map<String, Integer> col = new HashMap<>();
        List<String> header = rows.getFirst();
        for (int i = 0; i < header.size(); i++) {
            String name = header.get(i).toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
            String key = switch (name) {
                case "company", "companyname", "employer", "organization" -> "company";
                case "position", "title", "jobtitle", "role", "job" -> "title";
                case "status", "stage" -> "status";
                case "dateapplied", "applied", "date", "applieddate", "applicationdate" -> "date";
                case "location", "city" -> "location";
                case "link", "url", "joburl", "postingurl", "jobpostingurl" -> "url";
                case "nextstep", "nextaction" -> "next";
                case "nextstepdate", "nextactiondate", "followup", "followupdate" -> "nextDate";
                case "notes", "note", "comments" -> "notes";
                default -> null;
            };
            if (key != null) col.putIfAbsent(key, i);
        }
        if (!col.containsKey("company") || !col.containsKey("title")) {
            throw ApiException.badRequest("The first row must name the columns, including Company and Position.");
        }

        Long userId = currentUser.id();
        int imported = 0;
        List<ImportError> skipped = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            int line = r + 1;
            try {
                String company = Text.clip(get(row, col, "company"), 500);
                String title = Text.clip(get(row, col, "title"), 500);
                if (company == null || title == null) throw new IllegalArgumentException("Company and position are required");
                JobApplication a = new JobApplication();
                a.setUserId(userId);
                a.setCompanyName(company);
                a.setJobTitle(title);
                a.setStatus(parseStatus(get(row, col, "status")));
                LocalDate applied = parseDate(get(row, col, "date"));
                a.setDateApplied(applied == null ? LocalDate.now(clock) : applied);
                a.setStatusUpdatedAt(Instant.now(clock));
                a.setLocation(Text.clip(get(row, col, "location"), 500));
                String url = get(row, col, "url");
                if (!Text.isBlank(url)) {
                    String safe = Urls.safeHttp(url);
                    if (safe == null) throw new IllegalArgumentException("The link must start with http:// or https://");
                    a.setJobPostingUrl(safe);
                }
                a.setNextAction(Text.clip(get(row, col, "next"), 255));
                a.setNextActionDate(parseDate(get(row, col, "nextDate")));
                a.setNotes(Text.clip(get(row, col, "notes"), 10000));
                applications.save(a);
                events.save(new ApplicationEvent(a.getId(), null, a.getStatus(), "Imported from CSV"));
                imported++;
            } catch (IllegalArgumentException | DateTimeParseException e) {
                skipped.add(new ImportError(line, e.getMessage()));
            }
        }
        return new ImportResult(imported, skipped);
    }

    private static String get(List<String> row, Map<String, Integer> col, String key) {
        Integer i = col.get(key);
        return i == null || i >= row.size() ? null : row.get(i).strip();
    }

    /**
     * Maps free-text stages from other trackers by whole words, with final outcomes first:
     * "Rejected after interview" is REJECTED, and "Applied - no response" stays APPLIED.
     */
    static ApplicationStatus parseStatus(String raw) {
        if (Text.isBlank(raw)) return ApplicationStatus.APPLIED;
        Set<String> w = new HashSet<>(Arrays.asList(raw.toLowerCase(Locale.ROOT).split("[^a-z]+")));
        if (any(w, "offer", "offered", "accepted", "hired")) return ApplicationStatus.OFFER;
        if (any(w, "rejected", "rejection", "declined", "unsuccessful")) return ApplicationStatus.REJECTED;
        if (any(w, "withdrawn", "withdrew", "withdraw", "ghosted")) return ApplicationStatus.WITHDRAWN;
        if (any(w, "interview", "interviewing", "interviewed", "screen", "screening", "onsite")) return ApplicationStatus.INTERVIEWING;
        if (any(w, "assessment", "oa", "test", "challenge", "hackerrank", "codesignal")) return ApplicationStatus.ONLINE_ASSESSMENT;
        if (any(w, "applied", "submitted", "sent", "pending", "waiting")) return ApplicationStatus.APPLIED;
        throw new IllegalArgumentException("Unknown status \"" + Text.clip(raw, 40) + "\"");
    }

    private static boolean any(Set<String> words, String... options) {
        for (String o : options) if (words.contains(o)) return true;
        return false;
    }

    private static final List<DateTimeFormatter> DATES = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH));

    /** ISO and spelled-out dates only: 03/04/2026 is ambiguous (March or April?) so it is refused. */
    static LocalDate parseDate(String raw) {
        if (Text.isBlank(raw)) return null;
        for (DateTimeFormatter f : DATES) {
            try {
                return LocalDate.parse(raw.strip(), f);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        throw new DateTimeParseException("Use dates like 2026-10-14 or Oct 14, 2026", raw, 0);
    }

    // --- Calendar feed -------------------------------------------------------------

    /** Creates (or replaces) the private feed URL. The raw token is shown once; only its hash is kept. */
    @Transactional
    public String rotateCalendarToken() {
        User user = currentUser.get();
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        user.setCalendarTokenHash(Text.sha256(token));
        users.save(user);
        return properties.publicApiUrl() + "/calendar/feed/" + token + ".ics";
    }

    @Transactional
    public void revokeCalendarToken() {
        User user = currentUser.get();
        user.setCalendarTokenHash(null);
        users.save(user);
    }

    /** The feed for a token, or 404 for an unknown or revoked one (never confirming which). */
    @Transactional(readOnly = true)
    public String calendarFeed(String token) {
        User user = users.findByCalendarTokenHash(Text.sha256(token == null ? "" : token))
                .orElseThrow(() -> ApiException.notFound("Calendar"));
        Instant now = Instant.now(clock);
        ICalendar cal = new ICalendar("ApplyMate");
        for (JobApplication a : applications.findForUser(user.getId())) {
            if (a.getNextActionDate() == null || !a.getStatus().isOpen()) continue;
            String step = a.getNextAction() == null ? "Next step" : a.getNextAction();
            cal.allDayEvent("next-" + a.getId() + "@applymate", a.getNextActionDate(),
                    step + " · " + a.getCompanyName(),
                    a.getJobTitle() + " at " + a.getCompanyName(),
                    properties.frontendUrl() + "/applications/" + a.getId(), now);
        }
        return cal.build();
    }

    // --- Email drafts ---------------------------------------------------------------

    public enum EmailPurpose {
        FOLLOW_UP("A polite follow-up asking about the status of the application."),
        THANK_YOU("A thank-you note after an interview, mentioning something specific about the role."),
        WITHDRAW("A courteous note withdrawing from the process, leaving the door open."),
        ACCEPT_OFFER("Accepting the offer and asking about next steps."),
        DECLINE_OFFER("Gracefully declining the offer.");

        final String instruction;

        EmailPurpose(String instruction) {
            this.instruction = instruction;
        }
    }

    /** A draft to copy into the user's own email client. Nothing is sent or stored. */
    public EmailDraft emailDraft(Long applicationId, EmailPurpose purpose, String extra) {
        Long userId = currentUser.id();
        JobApplication app = applicationService.owned(applicationId);
        User user = currentUser.get();
        StringBuilder facts = new StringBuilder()
                .append("Candidate: ").append(user.getName() == null ? "(name not given)" : user.getName()).append('\n')
                .append("Role: ").append(app.getJobTitle()).append('\n')
                .append("Company: ").append(app.getCompanyName()).append('\n')
                .append("Current stage: ").append(app.getStatus()).append('\n')
                .append("Applied on: ").append(app.getDateApplied()).append('\n');
        communications.findByApplicationIdOrderByOccurredAtDescIdDesc(applicationId).stream().limit(3).forEach(c ->
                facts.append("Earlier ").append(c.getKind().name().toLowerCase(Locale.ROOT)).append(' ')
                        .append(c.getDirection() == CommunicationDirection.RECEIVED ? "from " : "to ")
                        .append(c.getContactName() == null ? "the employer" : c.getContactName())
                        .append(" on ").append(c.getOccurredAt().atZone(clock.getZone()).toLocalDate())
                        .append(c.getSubject() == null ? "" : ": " + c.getSubject()).append('\n'));
        if (!Text.isBlank(extra)) facts.append("The candidate adds: ").append(Text.clip(extra, 300)).append('\n');
        String advert = app.getJob() == null ? null : app.getJob().getDescription();
        EmailDraft draft = ai.emailDraft(userId, purpose.instruction, facts.toString(), advert).value();
        return new EmailDraft(Text.clipOrEmpty(draft.subject(), 200), Text.clipOrEmpty(draft.body(), 4000));
    }
}
