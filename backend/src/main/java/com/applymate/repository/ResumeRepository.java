package com.applymate.repository;

import com.applymate.model.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    Optional<Resume> findFirstByUserIdAndActiveTrueOrderByUploadedAtDesc(Long userId);

    List<Resume> findByUserIdOrderByUploadedAtDesc(Long userId);

    Optional<Resume> findByIdAndUserId(Long id, Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Resume r set r.active = false where r.userId = :userId and r.active = true")
    void deactivateAll(@Param("userId") Long userId);
}
