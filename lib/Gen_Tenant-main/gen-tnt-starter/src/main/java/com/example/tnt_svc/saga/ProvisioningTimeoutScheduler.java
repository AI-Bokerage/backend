package com.example.tnt_svc.saga;

import com.example.tnt_svc.domain.ProvisioningJob;
import com.example.tnt_svc.domain.ProvisioningJobStatus;
import com.example.tnt_svc.persistence.ProvisioningJobRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ProvisioningTimeoutScheduler {

    private final ProvisioningJobRepository jobRepository;
    private final ProvisioningSagaOrchestrator orchestrator;

    public ProvisioningTimeoutScheduler(ProvisioningJobRepository jobRepository, ProvisioningSagaOrchestrator orchestrator) {
        this.jobRepository = jobRepository;
        this.orchestrator = orchestrator;
    }

    @Scheduled(fixedDelayString = "${gentnt.provisioning.timeout-scheduler-interval-ms:60000}")
    public void handleExpiredJobs() {
        for (ProvisioningJob job : jobRepository.findByStatusAndExpiresAtBefore(ProvisioningJobStatus.IN_PROGRESS, Instant.now())) {
            orchestrator.handleTimeout(job.getId());
        }
    }
}
