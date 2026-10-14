package com.applymate.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A resume or cover letter for one job. {@code content} is what was generated and
 * is never overwritten; {@code editedContent} is the user's revision of it.
 */
@Entity
@Table(name = "generated_documents")
public class GeneratedDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(name = "resume_id")
    private Long resumeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentKind kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private JsonNode content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "edited_content", columnDefinition = "jsonb")
    private JsonNode editedContent;

    @Column(name = "model_used", length = 80)
    private String modelUsed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** What the user sees: their edit if they made one, otherwise the original. */
    public JsonNode currentContent() {
        return editedContent != null ? editedContent : content;
    }

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Job getJob() { return job; }
    public void setJob(Job job) { this.job = job; }

    public Long getResumeId() { return resumeId; }
    public void setResumeId(Long resumeId) { this.resumeId = resumeId; }

    public DocumentKind getKind() { return kind; }
    public void setKind(DocumentKind kind) { this.kind = kind; }

    public JsonNode getContent() { return content; }
    public void setContent(JsonNode content) { this.content = content; }

    public JsonNode getEditedContent() { return editedContent; }
    public void setEditedContent(JsonNode editedContent) { this.editedContent = editedContent; }

    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
