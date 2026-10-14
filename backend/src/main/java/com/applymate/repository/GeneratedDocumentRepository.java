package com.applymate.repository;

import com.applymate.model.GeneratedDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, Long> {
    @Query("select d from GeneratedDocument d join fetch d.job where d.id = :id and d.userId = :userId")
    Optional<GeneratedDocument> findForUser(@Param("id") Long id, @Param("userId") Long userId);

    @Query("select d from GeneratedDocument d where d.userId = :userId and d.job.id = :jobId order by d.createdAt desc")
    List<GeneratedDocument> findForUserAndJob(@Param("userId") Long userId, @Param("jobId") Long jobId);

    @Query("select d from GeneratedDocument d join fetch d.job where d.userId = :userId order by d.updatedAt desc")
    List<GeneratedDocument> findAllForUser(@Param("userId") Long userId);
}
