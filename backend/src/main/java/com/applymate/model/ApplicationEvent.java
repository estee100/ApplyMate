package com.applymate.model;

import jakarta.persistence.*;

import java.time.Instant;

/** One status change on an application. */
@Entity
@Table(name = "application_events")
public class ApplicationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    /** Null only for the event that created the application. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 50)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 50)
    private ApplicationStatus toStatus;

    @Column(length = 1000)
    private String note;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();

    protected ApplicationEvent() {}

    public ApplicationEvent(Long applicationId, ApplicationStatus fromStatus, ApplicationStatus toStatus, String note) {
        this.applicationId = applicationId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.note = note;
    }

    public Long getId() { return id; }
    public Long getApplicationId() { return applicationId; }
    public ApplicationStatus getFromStatus() { return fromStatus; }
    public ApplicationStatus getToStatus() { return toStatus; }
    public String getNote() { return note; }
    public Instant getChangedAt() { return changedAt; }
}
