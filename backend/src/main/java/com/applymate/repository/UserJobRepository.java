package com.applymate.repository;

import com.applymate.model.UserJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserJobRepository extends JpaRepository<UserJob, Long> {
    @Query("select u from UserJob u join fetch u.job where u.userId = :userId order by u.addedAt desc")
    List<UserJob> findForUser(@Param("userId") Long userId);

    Optional<UserJob> findByUserIdAndJobId(Long userId, Long jobId);

    boolean existsByUserIdAndJobId(Long userId, Long jobId);
}
