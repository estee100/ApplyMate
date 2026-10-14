package com.applymate.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Cached AI match analysis for one (user, job, resume). */
@Entity
@Table(name = "job_matches")
public class JobMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "resume_id", nullable = false)
    private Long resumeId;

    @Column(name = "match_percentage", nullable = false)
    private int matchPercentage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requirements_met", columnDefinition = "jsonb", nullable = false)
    private List<String> requirementsMet = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requirements_missing", columnDefinition = "jsonb", nullable = false)
    private List<String> requirementsMissing = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String rationale;

    @Column(name = "model_used", length = 80)
    private String modelUsed;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt = Instant.now();

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }

    public Long getResumeId() { return resumeId; }
    public void setResumeId(Long resumeId) { this.resumeId = resumeId; }

    public int getMatchPercentage() { return matchPercentage; }
    public void setMatchPercentage(int matchPercentage) { this.matchPercentage = matchPercentage; }

    public List<String> getRequirementsMet() { return requirementsMet; }
    public void setRequirementsMet(List<String> requirementsMet) { this.requirementsMet = requirementsMet; }

    public List<String> getRequirementsMissing() { return requirementsMissing; }
    public void setRequirementsMissing(List<String> requirementsMissing) { this.requirementsMissing = requirementsMissing; }

    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }

    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
}
