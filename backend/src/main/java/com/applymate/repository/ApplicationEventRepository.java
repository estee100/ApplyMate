package com.applymate.repository;

import com.applymate.model.ApplicationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ApplicationEventRepository extends JpaRepository<ApplicationEvent, Long> {
    List<ApplicationEvent> findByApplicationIdOrderByChangedAtAscIdAsc(Long applicationId);

    @Query("""
            select e from ApplicationEvent e
            where e.applicationId in (select a.id from JobApplication a where a.userId = :userId)
            order by e.changedAt asc, e.id asc""")
    List<ApplicationEvent> findForUser(@Param("userId") Long userId);
}
