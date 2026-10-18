package com.example.tnt_svc.saga;

import com.example.tnt_svc.domain.ProvisioningJob;
import com.example.tnt_svc.domain.ProvisioningJobStatus;
import com.example.tnt_svc.persistence.ProvisioningJobRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProvisioningTimeoutSchedulerTest {

    private final ProvisioningJobRepository jobRepository = mock(ProvisioningJobRepository.class);
    private final ProvisioningSagaOrchestrator orchestrator = mock(ProvisioningSagaOrchestrator.class);
    private final ProvisioningTimeoutScheduler scheduler = new ProvisioningTimeoutScheduler(jobRepository, orchestrator);

    @Test
    void handlesExpiredInProgressJobs() {
        UUID jobId = UUID.randomUUID();
        ProvisioningJob job = ProvisioningJob.builder()
            .id(jobId).tenantId(UUID.randomUUID()).status(ProvisioningJobStatus.IN_PROGRESS)
            .retryCount(0).maxRetries(3).callbackToken("tok").context(Map.of())
            .expiresAt(Instant.now().minusSeconds(60)).build();
        when(jobRepository.findByStatusAndExpiresAtBefore(eq(ProvisioningJobStatus.IN_PROGRESS), any())).thenReturn(List.of(job));

        scheduler.handleExpiredJobs();

        verify(orchestrator, times(1)).handleTimeout(jobId);
    }
}
