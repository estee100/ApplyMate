package com.applymate.model;

import jakarta.persistence.*;

import java.time.Instant;

/** A logged exchange with the employer: an email, call, meeting or message. */
@Entity
@Table(name = "communications")
public class Communication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommunicationKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CommunicationDirection direction;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "contact_name")
    private String contactName;

    @Column(length = 500)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }

    public CommunicationKind getKind() { return kind; }
    public void setKind(CommunicationKind kind) { this.kind = kind; }

    public CommunicationDirection getDirection() { return direction; }
    public void setDirection(CommunicationDirection direction) { this.direction = direction; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Instant getCreatedAt() { return createdAt; }
}
