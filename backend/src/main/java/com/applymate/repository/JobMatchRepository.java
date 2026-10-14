package com.applymate.repository;

import com.applymate.model.JobMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobMatchRepository extends JpaRepository<JobMatch, Long> {
    Optional<JobMatch> findByUserIdAndJobIdAndResumeId(Long userId, Long jobId, Long resumeId);

    /** Cards read scores in one query, never one call per card. */
    List<JobMatch> findByUserIdAndResumeIdAndJobIdIn(Long userId, Long resumeId, Collection<Long> jobIds);
}
