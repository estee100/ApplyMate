package com.applymate.repository;

import com.applymate.model.SavedJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SavedJobRepository extends JpaRepository<SavedJob, Long> {
    @Query("select s from SavedJob s join fetch s.job where s.userId = :userId order by s.savedAt desc")
    List<SavedJob> findForUser(@Param("userId") Long userId);

    Optional<SavedJob> findByUserIdAndJobId(Long userId, Long jobId);

    @Query("select s.job.id from SavedJob s where s.userId = :userId and s.job.id in :jobIds")
    Set<Long> savedJobIds(@Param("userId") Long userId, @Param("jobIds") Collection<Long> jobIds);

    long countByUserId(Long userId);
}
