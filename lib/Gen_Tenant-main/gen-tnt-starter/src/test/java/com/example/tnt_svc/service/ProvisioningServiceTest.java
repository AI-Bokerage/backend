// gen-tnt-starter/src/test/java/com/example/tnt_svc/service/ProvisioningServiceTest.java
package com.example.tnt_svc.service;

import com.example.tnt_svc.domain.ProvisioningJob;
import com.example.tnt_svc.domain.ProvisioningJobStatus;
import com.example.tnt_svc.domain.exception.ProvisioningJobNotFoundException;
import com.example.tnt_svc.domain.exception.ProvisioningJobNotRetryableException;
import com.example.tnt_svc.persistence.ProvisioningJobRepository;
import com.example.tnt_svc.persistence.ProvisioningStepRepository;
import com.example.tnt_svc.saga.ProvisioningSagaOrchestrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProvisioningServiceTest {

    private final ProvisioningJobRepository jobRepository = mock(ProvisioningJobRepository.class);
    private final ProvisioningStepRepository stepRepository = mock(ProvisioningStepRepository.class);
    private final ProvisioningSagaOrchestrator orchestrator = mock(ProvisioningSagaOrchestrator.class);
    private final ProvisioningService service = new ProvisioningService(jobRepository, stepRepository, orchestrator);

    private ProvisioningJob jobWithStatus(UUID jobId, ProvisioningJobStatus status) {
        return ProvisioningJob.builder()
            .id(jobId)
            .tenantId(UUID.randomUUID())
            .status(status)
            .retryCount(0)
            .maxRetries(3)
            .callbackToken("tok")
            .context(Map.of())
            .build();
    }

    @ParameterizedTest
    @EnumSource(value = ProvisioningJobStatus.class, names = {"IN_PROGRESS", "COMPLETED", "PENDING", "DEAD"})
    void manualRetryOnNonFailedJobIsRejectedWithoutTouchingOrchestrator(ProvisioningJobStatus status) {
        // C1: the manual-retry HTTP endpoint has no scheduler-style pre-filter to FAILED jobs.
        // retryProvisioning()'s unconditional markDead() branch must never be reached for a
        // healthy (or already-dead) job coming through this path.
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(jobWithStatus(jobId, status)));

        assertThatThrownBy(() -> service.manualRetry(jobId))
            .isInstanceOf(ProvisioningJobNotRetryableException.class);

        verify(orchestrator, never()).retryProvisioning(jobId);
    }

    @Test
    void manualRetryOnFailedJobDelegatesToOrchestrator() {
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(jobWithStatus(jobId, ProvisioningJobStatus.FAILED)));

        service.manualRetry(jobId);

        verify(orchestrator).retryProvisioning(jobId);
    }

    @Test
    void getStepsOnUnknownJobThrowsNotFoundInsteadOfReturningEmptyList() {
        // A caller couldn't otherwise distinguish "job exists with 0 steps" from "no such job".
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSteps(jobId))
            .isInstanceOf(ProvisioningJobNotFoundException.class);

        verify(stepRepository, never()).findByJobIdOrderByStepOrderAsc(jobId);
    }
}
