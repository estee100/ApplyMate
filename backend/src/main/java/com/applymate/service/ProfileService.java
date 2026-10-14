package com.applymate.service;

import com.applymate.ai.AiQuota;
import com.applymate.dto.ProfileDtos.*;
import com.applymate.model.User;
import com.applymate.repository.SkillProfileRepository;
import com.applymate.repository.UserRepository;
import com.applymate.util.Text;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final CurrentUser currentUser;
    private final UserRepository users;
    private final ResumeService resumes;
    private final SkillProfileRepository profiles;
    private final AiQuota quota;
    private final AvatarService avatars;

    public ProfileService(CurrentUser currentUser, UserRepository users, ResumeService resumes,
                          SkillProfileRepository profiles, AiQuota quota, AvatarService avatars) {
        this.avatars = avatars;
        this.currentUser = currentUser;
        this.users = users;
        this.resumes = resumes;
        this.profiles = profiles;
        this.quota = quota;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get() {
        return toResponse(currentUser.get());
    }

    @Transactional
    public ProfileResponse update(ProfileUpdate update) {
        User user = currentUser.get();
        if (update.name() != null) user.setName(Text.clip(update.name(), 120));
        if (update.preferredLocation() != null) user.setPreferredLocation(Text.clip(update.preferredLocation(), 255));
        if (update.includeRemote() != null) user.setIncludeRemote(update.includeRemote());
        if (update.emailReminders() != null) user.setEmailReminders(update.emailReminders());
        users.save(user);
        return toResponse(user);
    }

    private ProfileResponse toResponse(User user) {
        var resume = resumes.active(user.getId()).orElse(null);
        var profile = resume == null ? null : profiles.findByResumeId(resume.getId()).orElse(null);
        var usage = quota.usage(user.getId());
        return new ProfileResponse(
                user.getEmail(),
                user.getName(),
                user.getPreferredLocation(),
                user.isIncludeRemote(),
                user.isEmailReminders(),
                resume == null ? null : resumes.toResponse(resume),
                profile == null ? null : resumes.toResponse(profile),
                new AiUsageResponse(usage.enabled(), usage.usedToday(), usage.dailyLimit()),
                user.getCreatedAt(),
                avatars.updatedAt(user.getId()).orElse(null)
        );
    }
}
