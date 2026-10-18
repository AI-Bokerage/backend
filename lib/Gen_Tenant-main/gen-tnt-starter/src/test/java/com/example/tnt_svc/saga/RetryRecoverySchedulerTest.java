package com.example.tnt_svc.saga;

import com.example.tnt_svc.domain.ProvisioningJob;
import com.example.tnt_svc.domain.ProvisioningJobStatus;
import com.example.tnt_svc.persistence.ProvisioningJobRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetryRecoverySchedulerTest {

    private final ProvisioningJobRepository jobRepository = mock(ProvisioningJobRepository.class);
    private final ProvisioningSagaOrchestrator orchestrator = mock(ProvisioningSagaOrchestrator.class);
    private final RetryRecoveryScheduler scheduler = new RetryRecoveryScheduler(jobRepository, orchestrator);

    // The scheduler itself doesn't decide retry-vs-dead — it just hands every
    // FAILED job to the orchestrator, which re-fetches fresh state and makes
    // that call internally (see ProvisioningSagaOrchestratorTest for the
    // retries-exhausted-marks-dead case). Two jobs here only to confirm the
    // scheduler loops over all of them, not just the first.
    @Test
    void handsEveryFailedJobToTheOrchestrator() {
        UUID jobId1 = UUID.randomUUID();
        UUID jobId2 = UUID.randomUUID();
        ProvisioningJob job1 = ProvisioningJob.builder()
            .id(jobId1).tenantId(UUID.randomUUID()).status(ProvisioningJobStatus.FAILED)
            .retryCount(1).maxRetries(3).callbackToken("tok").context(Map.of()).build();
        ProvisioningJob job2 = ProvisioningJob.builder()
            .id(jobId2).tenantId(UUID.randomUUID()).status(ProvisioningJobStatus.FAILED)
            .retryCount(3).maxRetries(3).callbackToken("tok").context(Map.of()).build();
        when(jobRepository.findByStatus(ProvisioningJobStatus.FAILED)).thenReturn(List.of(job1, job2));

        scheduler.recoverFailedJobs();

        verify(orchestrator, times(1)).retryProvisioning(jobId1);
        verify(orchestrator, times(1)).retryProvisioning(jobId2);
    }
}
