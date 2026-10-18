package com.example.admsvc.infrastructure.persistence.repository;

import com.example.admsvc.infrastructure.persistence.entity.OffboardingStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OffboardingStepRepository extends JpaRepository<OffboardingStepEntity, UUID> {

    List<OffboardingStepEntity> findAllByJobIdOrderBySequenceAsc(UUID jobId);
}
