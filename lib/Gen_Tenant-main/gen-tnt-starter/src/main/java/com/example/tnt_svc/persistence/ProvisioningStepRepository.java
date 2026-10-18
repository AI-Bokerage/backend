package com.example.tnt_svc.persistence;

import com.example.tnt_svc.domain.ProvisioningStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProvisioningStepRepository extends JpaRepository<ProvisioningStep, UUID> {
    List<ProvisioningStep> findByJobIdOrderByStepOrderAsc(UUID jobId);

    Optional<ProvisioningStep> findByJobIdAndStepName(UUID jobId, String stepName);
}
