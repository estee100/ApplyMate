package com.applymate.model;

import jakarta.persistence.*;

import java.time.Instant;

/** A job this user shortlisted. */
@Entity
@Table(name = "saved_jobs")
public class SavedJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(name = "saved_at", nullable = false, updatable = false)
    private Instant savedAt = Instant.now();

    protected SavedJob() {}

    public SavedJob(Long userId, Job job) {
        this.userId = userId;
        this.job = job;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Job getJob() { return job; }
    public Instant getSavedAt() { return savedAt; }
}
