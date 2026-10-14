package com.applymate.repository;

import com.applymate.model.InterviewPrep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewPrepRepository extends JpaRepository<InterviewPrep, Long> {
    Optional<InterviewPrep> findByApplicationId(Long applicationId);
}
