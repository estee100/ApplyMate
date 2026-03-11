package com.applymate.repository;

import com.applymate.model.ApplicationStatus;
import com.applymate.model.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    List<JobApplication> findByUserIdOrderByDateAppliedDesc(Long userId);
    List<JobApplication> findByUserIdAndStatusOrderByDateAppliedDesc(Long userId, ApplicationStatus status);
    Optional<JobApplication> findByIdAndUserId(Long id, Long userId);
}
