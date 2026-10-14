package com.applymate.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A job the user has applied to. Title, company, location and URL are stored here
 * so an application logged by hand needs no Job row; {@code job} links to the job
 * in the app when there is one.
 */
@Entity
@Table(name = "job_applications")
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private Job job;

    @Column(name = "resume_id")
    private Long resumeId;

    @Column(name = "job_title", nullable = false, length = 500)
    private String jobTitle;

    @Column(name = "company_name", nullable = false, length = 500)
    private String companyName;

    @Column(length = 500)
    private String location;

    @Column(name = "job_posting_url", length = 2048)
    private String jobPostingUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ApplicationStatus status = ApplicationStatus.APPLIED;

    @Column(name = "date_applied", nullable = false)
    private LocalDate dateApplied;

    @Column(name = "status_updated_at", nullable = false)
    private Instant statusUpdatedAt = Instant.now();

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "next_action")
    private String nextAction;

    @Column(name = "next_action_date")
    private LocalDate nextActionDate;

    @Column(name = "reminder_sent_for")
    private LocalDate reminderSentFor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Job getJob() { return job; }
    public void setJob(Job job) { this.job = job; }

    public Long getResumeId() { return resumeId; }
    public void setResumeId(Long resumeId) { this.resumeId = resumeId; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getJobPostingUrl() { return jobPostingUrl; }
    public void setJobPostingUrl(String jobPostingUrl) { this.jobPostingUrl = jobPostingUrl; }

    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }

    public LocalDate getDateApplied() { return dateApplied; }
    public void setDateApplied(LocalDate dateApplied) { this.dateApplied = dateApplied; }

    public Instant getStatusUpdatedAt() { return statusUpdatedAt; }
    public void setStatusUpdatedAt(Instant statusUpdatedAt) { this.statusUpdatedAt = statusUpdatedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getNextAction() { return nextAction; }
    public void setNextAction(String nextAction) { this.nextAction = nextAction; }

    public LocalDate getNextActionDate() { return nextActionDate; }
    public void setNextActionDate(LocalDate nextActionDate) { this.nextActionDate = nextActionDate; }

    public LocalDate getReminderSentFor() { return reminderSentFor; }
    public void setReminderSentFor(LocalDate reminderSentFor) { this.reminderSentFor = reminderSentFor; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
