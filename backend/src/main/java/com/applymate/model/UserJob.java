package com.applymate.model;

import jakarta.persistence.*;

import java.time.Instant;

/** A job this user pasted into the app. */
@Entity
@Table(name = "user_jobs")
public class UserJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt = Instant.now();

    protected UserJob() {}

    public UserJob(Long userId, Job job) {
        this.userId = userId;
        this.job = job;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Job getJob() { return job; }
    public Instant getAddedAt() { return addedAt; }
}
