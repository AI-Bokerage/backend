package com.example.admsvc.application.impl;

import com.example.admsvc.domain.enums.OffboardingJobStatus;
import com.example.admsvc.domain.enums.OffboardingStepStatus;
import com.example.admsvc.domain.port.OffboardingEventPublisher;
import com.example.admsvc.domain.port.OffboardingStepHandler;
import com.example.admsvc.domain.port.SessionRevocationGateway;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingStepEntity;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingJobRepository;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingStepRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OffboardingStepExecutorTest {

    private final OffboardingJobRepository jobRepository = mock(OffboardingJobRepository.class);
    private final OffboardingStepRepository stepRepository = mock(OffboardingStepRepository.class);
    private final SessionRevocationGateway sessionRevocationGateway = mock(SessionRevocationGateway.class);
    private final OffboardingEventPublisher eventPublisher = mock(OffboardingEventPublisher.class);

    private OffboardingJobEntity newJob(int attemptCount, int maxAttempts) {
        return OffboardingJobEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .initiatedBy(UUID.randomUUID())
                .reason("test")
                .status(OffboardingJobStatus.PENDING)
                .attemptCount(attemptCount)
                .maxAttempts(maxAttempts)
                .build();
    }

    private OffboardingStepEntity newStep(UUID jobId, int sequence, String stepName, OffboardingStepStatus status) {
        return OffboardingStepEntity.builder()
                .id(UUID.randomUUID())
                .jobId(jobId)
                .sequence(sequence)
                .stepName(stepName)
                .status(status)
                .attemptNumber(0)
                .build();
    }

    @Test
    void resumeSkipsCompletedStepsAndOnlyRunsRemaining() {
        OffboardingJobEntity job = newJob(0, 5);
        OffboardingStepEntity completedRevocation =
                newStep(job.getId(), 0, "SESSION_REVOCATION", OffboardingStepStatus.COMPLETED);
        OffboardingStepEntity pendingHandlerStep =
                newStep(job.getId(), 1, "TASK_X", OffboardingStepStatus.PENDING);

        OffboardingStepHandler handler = mock(OffboardingStepHandler.class);
        when(handler.stepName()).thenReturn("TASK_X");

        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(stepRepository.findAllByJobIdOrderBySequenceAsc(job.getId()))
                .thenReturn(List.of(completedRevocation, pendingHandlerStep));

        OffboardingStepExecutor executor = new OffboardingStepExecutor(
                jobRepository, stepRepository, sessionRevocationGateway, List.of(handler), eventPublisher);

        executor.execute(job.getTenantId(), job.getId());

        verify(sessionRevocationGateway, never()).revoke(any(), any());
        verify(handler).handle(job.getTenantId(), job.getUserId(), job.getInitiatedBy());
        assertThat(job.getStatus()).isEqualTo(OffboardingJobStatus.COMPLETED);
        assertThat(pendingHandlerStep.getStatus()).isEqualTo(OffboardingStepStatus.COMPLETED);
    }

    @Test
    void backoffIsFiveMinutesTimesAttemptCountCappedAtThirty() {
        OffboardingJobEntity job = newJob(4, 10);
        OffboardingStepEntity failingStep =
                newStep(job.getId(), 0, "SESSION_REVOCATION", OffboardingStepStatus.PENDING);

        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(stepRepository.findAllByJobIdOrderBySequenceAsc(job.getId())).thenReturn(List.of(failingStep));
        doThrow(new RuntimeException("boom")).when(sessionRevocationGateway).revoke(any(), any());

        OffboardingStepExecutor executor = new OffboardingStepExecutor(
                jobRepository, stepRepository, sessionRevocationGateway, List.of(), eventPublisher);

        Instant before = Instant.now();
        executor.execute(job.getTenantId(), job.getId());

        assertThat(job.getAttemptCount()).isEqualTo(5);
        assertThat(job.getStatus()).isEqualTo(OffboardingJobStatus.FAILED);
        assertThat(job.getNextRetryAt()).isAfterOrEqualTo(before.plusSeconds(25 * 60));
        assertThat(failingStep.getStatus()).isEqualTo(OffboardingStepStatus.FAILED);
        assertThat(failingStep.getErrorMessage()).isEqualTo("boom");
    }

    @Test
    void jobBecomesPermanentlyFailedWhenAttemptCountReachesMaxAttempts() {
        OffboardingJobEntity job = newJob(2, 3);
        OffboardingStepEntity failingStep =
                newStep(job.getId(), 0, "SESSION_REVOCATION", OffboardingStepStatus.PENDING);

        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(stepRepository.findAllByJobIdOrderBySequenceAsc(job.getId())).thenReturn(List.of(failingStep));
        doThrow(new RuntimeException("boom")).when(sessionRevocationGateway).revoke(any(), any());

        OffboardingStepExecutor executor = new OffboardingStepExecutor(
                jobRepository, stepRepository, sessionRevocationGateway, List.of(), eventPublisher);

        executor.execute(job.getTenantId(), job.getId());

        assertThat(job.getAttemptCount()).isEqualTo(3);
        assertThat(job.getStatus()).isEqualTo(OffboardingJobStatus.PERMANENTLY_FAILED);
        assertThat(job.getNextRetryAt()).isNull();
    }

    @Test
    void executeNeverPropagatesAStepHandlerException() {
        OffboardingJobEntity job = newJob(0, 5);
        OffboardingStepEntity failingStep =
                newStep(job.getId(), 0, "SESSION_REVOCATION", OffboardingStepStatus.PENDING);

        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(stepRepository.findAllByJobIdOrderBySequenceAsc(job.getId())).thenReturn(List.of(failingStep));
        doThrow(new RuntimeException("boom")).when(sessionRevocationGateway).revoke(any(), any());

        OffboardingStepExecutor executor = new OffboardingStepExecutor(
                jobRepository, stepRepository, sessionRevocationGateway, List.of(), eventPublisher);

        assertThatCode(() -> executor.execute(job.getTenantId(), job.getId())).doesNotThrowAnyException();
    }

    @Test
    void terminalJobsAreANoOp() {
        OffboardingJobEntity job = newJob(5, 5);
        job.setStatus(OffboardingJobStatus.PERMANENTLY_FAILED);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        OffboardingStepExecutor executor = new OffboardingStepExecutor(
                jobRepository, stepRepository, sessionRevocationGateway, List.of(), eventPublisher);

        executor.execute(job.getTenantId(), job.getId());

        verifyNoInteractions(sessionRevocationGateway);
        verify(stepRepository, never()).findAllByJobIdOrderBySequenceAsc(any());
    }
}
