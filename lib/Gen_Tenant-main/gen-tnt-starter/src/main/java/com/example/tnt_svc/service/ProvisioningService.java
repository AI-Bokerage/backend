// gen-tnt-starter/src/main/java/com/example/tnt_svc/service/ProvisioningService.java
package com.example.tnt_svc.service;

import com.example.tnt_svc.domain.ProvisioningJob;
import com.example.tnt_svc.domain.ProvisioningJobStatus;
import com.example.tnt_svc.domain.ProvisioningStep;
import com.example.tnt_svc.domain.exception.ProvisioningJobNotFoundException;
import com.example.tnt_svc.domain.exception.ProvisioningJobNotRetryableException;
import com.example.tnt_svc.persistence.ProvisioningJobRepository;
import com.example.tnt_svc.persistence.ProvisioningStepRepository;
import com.example.tnt_svc.saga.ProvisioningSagaOrchestrator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProvisioningService {

    private final ProvisioningJobRepository jobRepository;
    private final ProvisioningStepRepository stepRepository;
    private final ProvisioningSagaOrchestrator orchestrator;

    public ProvisioningService(
        ProvisioningJobRepository jobRepository,
        ProvisioningStepRepository stepRepository,
        ProvisioningSagaOrchestrator orchestrator
    ) {
        this.jobRepository = jobRepository;
        this.stepRepository = stepRepository;
        this.orchestrator = orchestrator;
    }

    public ProvisioningJob getJob(UUID id) {
        return jobRepository.findById(id).orElseThrow(() -> new ProvisioningJobNotFoundException(id));
    }

    public List<ProvisioningStep> getSteps(UUID jobId) {
        getJob(jobId);
        return stepRepository.findByJobIdOrderByStepOrderAsc(jobId);
    }

    public void manualRetry(UUID jobId) {
        ProvisioningJob job = getJob(jobId);
        if (job.getStatus() != ProvisioningJobStatus.FAILED) {
            // retryProvisioning()'s unconditional markDead() branch is only safe for jobs the
            // scheduler pre-filtered to FAILED; this HTTP-reachable path must enforce that itself
            // or a retry call on a healthy IN_PROGRESS/COMPLETED job would kill it outright.
            throw new ProvisioningJobNotRetryableException(jobId, job.getStatus());
        }
        orchestrator.retryProvisioning(jobId);
    }

    public List<ProvisioningJob> listFailed() {
        return jobRepository.findByStatusIn(List.of(ProvisioningJobStatus.FAILED, ProvisioningJobStatus.DEAD));
    }
}
