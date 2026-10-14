package com.applymate.repository;

import com.applymate.model.ApplicationStatus;
import com.applymate.model.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    List<JobApplication> findByUserIdOrderByDateAppliedDesc(Long userId);
    List<JobApplication> findByUserIdAndStatusOrderByDateAppliedDesc(Long userId, ApplicationStatus status);
    Optional<JobApplication> findByIdAndUserId(Long id, Long userId);

    /** Explicit JPQL — entity has <code>user</code>, not <code>userId</code>; derived countByUserId can fail at runtime. */
    @Query("select count(j) from JobApplication j where j.user.id = :userId")
    long countForUser(@Param("userId") Long userId);

    @Query("select count(j) from JobApplication j where j.user.id = :userId and j.status = :status")
    long countForUserAndStatus(@Param("userId") Long userId, @Param("status") ApplicationStatus status);

    @Query(value = """
            SELECT TO_CHAR(date_applied, 'YYYY-MM') AS month, COUNT(*) AS cnt
            FROM job_applications
            WHERE user_id = :userId
            GROUP BY TO_CHAR(date_applied, 'YYYY-MM')
            ORDER BY month
            """, nativeQuery = true)
    List<Object[]> countByMonthForUser(@Param("userId") Long userId);
}
