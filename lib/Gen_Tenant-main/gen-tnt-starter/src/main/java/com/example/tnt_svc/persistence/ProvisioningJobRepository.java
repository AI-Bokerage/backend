package com.example.tnt_svc.persistence;

import com.example.tnt_svc.domain.ProvisioningJob;
import com.example.tnt_svc.domain.ProvisioningJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProvisioningJobRepository extends JpaRepository<ProvisioningJob, UUID> {
    List<ProvisioningJob> findByStatus(ProvisioningJobStatus status);

    List<ProvisioningJob> findByStatusAndExpiresAtBefore(ProvisioningJobStatus status, Instant time);

    List<ProvisioningJob> findByStatusIn(List<ProvisioningJobStatus> statuses);
}
