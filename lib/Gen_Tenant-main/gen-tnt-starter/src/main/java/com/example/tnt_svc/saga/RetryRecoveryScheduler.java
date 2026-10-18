package com.example.tnt_svc.saga;

import com.example.tnt_svc.domain.ProvisioningJob;
import com.example.tnt_svc.domain.ProvisioningJobStatus;
import com.example.tnt_svc.persistence.ProvisioningJobRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RetryRecoveryScheduler {

    private final ProvisioningJobRepository jobRepository;
    private final ProvisioningSagaOrchestrator orchestrator;

    public RetryRecoveryScheduler(ProvisioningJobRepository jobRepository, ProvisioningSagaOrchestrator orchestrator) {
        this.jobRepository = jobRepository;
        this.orchestrator = orchestrator;
    }

    @Scheduled(fixedDelayString = "${gentnt.provisioning.retry-scheduler-interval-ms:120000}")
    public void recoverFailedJobs() {
        // retryProvisioning() itself checks canRetry() and marks the job DEAD
        // when it's false — no branching needed here, every FAILED job just
        // gets handed to the orchestrator.
        for (ProvisioningJob job : jobRepository.findByStatus(ProvisioningJobStatus.FAILED)) {
            orchestrator.retryProvisioning(job.getId());
        }
    }
}
