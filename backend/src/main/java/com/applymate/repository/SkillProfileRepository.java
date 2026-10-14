package com.applymate.repository;

import com.applymate.model.SkillProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SkillProfileRepository extends JpaRepository<SkillProfile, Long> {
    Optional<SkillProfile> findByResumeId(Long resumeId);
}
