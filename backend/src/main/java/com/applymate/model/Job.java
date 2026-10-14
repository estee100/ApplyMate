package com.applymate.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A job listing. Shared between users and deduplicated on (source, externalId):
 * per-user state (saved, pasted, matched, applied) lives in other tables.
 */
@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobSource source;

    @Column(name = "external_id", nullable = false, length = 768)
    private String externalId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false)
    private String company;

    @Column(name = "company_logo_url", length = 2048)
    private String companyLogoUrl;

    private String location;

    @Column(length = 120)
    private String salary;

    /** The direct apply link. Only http(s) is ever stored, since it is rendered as an href. */
    @Column(length = 2048)
    private String url;

    /** Where the apply link goes, e.g. "LinkedIn", so the button can say so. */
    @Column(length = 120)
    private String publisher;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", length = 20)
    private JobType jobType;

    @Column(name = "posted_date")
    private LocalDate postedDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }

    public JobSource getSource() { return source; }
    public void setSource(JobSource source) { this.source = source; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getCompanyLogoUrl() { return companyLogoUrl; }
    public void setCompanyLogoUrl(String companyLogoUrl) { this.companyLogoUrl = companyLogoUrl; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getSalary() { return salary; }
    public void setSalary(String salary) { this.salary = salary; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }

    public JobType getJobType() { return jobType; }
    public void setJobType(JobType jobType) { this.jobType = jobType; }

    public LocalDate getPostedDate() { return postedDate; }
    public void setPostedDate(LocalDate postedDate) { this.postedDate = postedDate; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getCreatedAt() { return createdAt; }
}
