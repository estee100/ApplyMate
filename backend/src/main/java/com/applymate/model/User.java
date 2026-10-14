package com.applymate.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(length = 120)
    private String name;

    /** Embedded in every JWT; incrementing it revokes all existing tokens. */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    /** Steers recommendations; blank means use the location on the resume. */
    @Column(name = "preferred_location")
    private String preferredLocation;

    @Column(name = "include_remote", nullable = false)
    private boolean includeRemote = true;

    @Column(name = "email_reminders", nullable = false)
    private boolean emailReminders = false;

    @Column(name = "weekly_goal", nullable = false)
    private int weeklyGoal = 5;

    @Column(name = "calendar_token_hash", length = 64, unique = true)
    private String calendarTokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getTokenVersion() { return tokenVersion; }
    public void bumpTokenVersion() { this.tokenVersion++; }

    public String getPreferredLocation() { return preferredLocation; }
    public void setPreferredLocation(String preferredLocation) { this.preferredLocation = preferredLocation; }

    public boolean isIncludeRemote() { return includeRemote; }
    public void setIncludeRemote(boolean includeRemote) { this.includeRemote = includeRemote; }

    public boolean isEmailReminders() { return emailReminders; }
    public void setEmailReminders(boolean emailReminders) { this.emailReminders = emailReminders; }

    public int getWeeklyGoal() { return weeklyGoal; }
    public void setWeeklyGoal(int weeklyGoal) { this.weeklyGoal = weeklyGoal; }

    public String getCalendarTokenHash() { return calendarTokenHash; }
    public void setCalendarTokenHash(String calendarTokenHash) { this.calendarTokenHash = calendarTokenHash; }

    public Instant getCreatedAt() { return createdAt; }
}
