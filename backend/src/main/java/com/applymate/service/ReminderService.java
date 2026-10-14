package com.applymate.service;

import com.applymate.config.AppProperties;
import com.applymate.model.ApplicationStatus;
import com.applymate.model.JobApplication;
import com.applymate.model.User;
import com.applymate.repository.JobApplicationRepository;
import com.applymate.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;

/**
 * Opt-in reminder emails for a tracked application's next step (an interview, an
 * offer deadline). Driven by the user's own next-action date, so a reminder always
 * refers to something they chose to track. Each date is reminded once.
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM");

    private final JobApplicationRepository applications;
    private final UserRepository users;
    private final MailService mail;
    private final AppProperties properties;
    private final Clock clock;

    public ReminderService(JobApplicationRepository applications, UserRepository users, MailService mail,
                           AppProperties properties, Clock clock) {
        this.applications = applications;
        this.users = users;
        this.mail = mail;
        this.properties = properties;
        this.clock = clock;
    }

    /** Hourly: anything due today or tomorrow that has not been reminded for that date. */
    @Scheduled(cron = "${app.reminders.cron:0 0 * * * *}")
    @Transactional
    public void sendDueReminders() {
        if (!mail.enabled()) return;
        LocalDate today = LocalDate.now(clock);
        List<JobApplication> due = applications.findDueForReminder(today, today.plusDays(1),
                EnumSet.of(ApplicationStatus.APPLIED, ApplicationStatus.ONLINE_ASSESSMENT, ApplicationStatus.INTERVIEWING));
        int sent = 0;
        for (JobApplication app : due) {
            User user = users.findById(app.getUserId()).orElse(null);
            if (user == null || !user.isEmailReminders()) continue;
            String step = app.getNextAction() == null ? "Next step" : app.getNextAction();
            String when = app.getNextActionDate().equals(today) ? "today" : "tomorrow, " + DAY.format(app.getNextActionDate());
            // The dedupe key makes this idempotent: if two instances run the job at once,
            // or it runs again before the flag below commits, only one email is queued.
            mail.send(user.getEmail(), "Reminder: " + step + " for " + app.getCompanyName(),
                    step + " for " + app.getJobTitle() + " at " + app.getCompanyName() + " is " + when + ".\n\n"
                            + "Open it in ApplyMate: " + properties.frontendUrl() + "/applications/" + app.getId() + "\n\n"
                            + "You can turn reminder emails off on your Profile page.",
                    null, "reminder:" + app.getId() + ":" + app.getNextActionDate());
            app.setReminderSentFor(app.getNextActionDate());
            sent++;
        }
        if (sent > 0) log.info("Sent {} reminder emails", sent);
    }
}
