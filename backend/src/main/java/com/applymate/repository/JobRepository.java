package com.applymate.repository;

import com.applymate.model.Job;
import com.applymate.model.JobSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {
    Optional<Job> findBySourceAndExternalId(JobSource source, String externalId);

    List<Job> findBySourceAndExternalIdIn(JobSource source, Collection<String> externalIds);
}
