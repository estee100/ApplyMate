package com.applymate.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * An uploaded CV. Only the extracted text is kept, not the file.
 * {@code masterContent} is the user's structured master resume, which every
 * tailored resume starts from.
 */
@Entity
@Table(name = "resumes")
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "master_content", columnDefinition = "jsonb")
    private JsonNode masterContent;

    @Column(nullable = false)
    private boolean active = true;

    /** Optimistic lock: Hibernate adds "where version = ?" to every update. */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();

    public Long getId() { return id; }

    public long getVersion() { return version; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public JsonNode getMasterContent() { return masterContent; }
    public void setMasterContent(JsonNode masterContent) { this.masterContent = masterContent; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getUploadedAt() { return uploadedAt; }
}
