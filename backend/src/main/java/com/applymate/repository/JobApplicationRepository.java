package com.applymate.repository;

import com.applymate.model.ApplicationStatus;
import com.applymate.model.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    @Query("select a from JobApplication a left join fetch a.job where a.userId = :userId order by a.statusUpdatedAt desc")
    List<JobApplication> findForUser(@Param("userId") Long userId);

    @Query("""
            select a from JobApplication a left join fetch a.job
            where a.userId = :userId and a.status = :status order by a.statusUpdatedAt desc""")
    List<JobApplication> findForUserAndStatus(@Param("userId") Long userId, @Param("status") ApplicationStatus status);

    @Query("select a from JobApplication a left join fetch a.job where a.id = :id and a.userId = :userId")
    Optional<JobApplication> findOneForUser(@Param("id") Long id, @Param("userId") Long userId);

    @Query("select a from JobApplication a where a.userId = :userId and a.job.id = :jobId")
    Optional<JobApplication> findForUserAndJob(@Param("userId") Long userId, @Param("jobId") Long jobId);

    @Query("select a from JobApplication a where a.userId = :userId and a.job.id in :jobIds")
    List<JobApplication> findForUserAndJobs(@Param("userId") Long userId, @Param("jobIds") Collection<Long> jobIds);

    /** Open applications whose next step falls in [from, to] and has not been reminded yet. */
    @Query("""
            select a from JobApplication a
            where a.nextActionDate between :from and :to
              and a.status in :open
              and (a.reminderSentFor is null or a.reminderSentFor <> a.nextActionDate)""")
    List<JobApplication> findDueForReminder(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                            @Param("open") Collection<ApplicationStatus> open);
}
