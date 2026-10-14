package com.applymate.repository;

import com.applymate.model.Communication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunicationRepository extends JpaRepository<Communication, Long> {
    List<Communication> findByApplicationIdOrderByOccurredAtDescIdDesc(Long applicationId);
}
