package com.example.admsvc.application.impl;

import com.example.admsvc.domain.port.OffboardingStepHandler;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingStepEntity;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingJobRepository;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingStepRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Plain Mockito unit tests — no Spring context, no database. Covers what
 * OffboardingIntegrationTest (Testcontainers) cannot isolate cheaply:
 * step-zero ordering independent of handler registration order, and that
 * an afterCommit synchronization is actually registered.
 */
class OffboardingServiceImplTest {

    private final OffboardingJobRepository jobRepository = mock(OffboardingJobRepository.class);
    private final OffboardingStepRepository stepRepository = mock(OffboardingStepRepository.class);
    private final OffboardingStepExecutor stepExecutor = mock(OffboardingStepExecutor.class);

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        // Guarded: the no-synchronization test clears it mid-test, and
        // clearSynchronization() throws if called again when already inactive.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private OffboardingStepHandler handler(String name) {
        OffboardingStepHandler handler = mock(OffboardingStepHandler.class);
        when(handler.stepName()).thenReturn(name);
        return handler;
    }

    @Test
    void sessionRevocationIsAlwaysSequenceZeroRegardlessOfHandlerRegistrationOrder() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID initiatedBy = UUID.randomUUID();
        OffboardingStepHandler handlerB = handler("TASK_B");
        OffboardingStepHandler handlerA = handler("TASK_A");

        when(jobRepository.saveAndFlush(any())).thenAnswer(inv -> {
            OffboardingJobEntity job = inv.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });

        OffboardingServiceImpl service = new OffboardingServiceImpl(
                jobRepository, stepRepository, List.of(handlerB, handlerA), stepExecutor);

        service.initiate(tenantId, userId, initiatedBy, "left the company");

        ArgumentCaptor<OffboardingStepEntity> captor = ArgumentCaptor.forClass(OffboardingStepEntity.class);
        verify(stepRepository, times(3)).save(captor.capture());
        List<OffboardingStepEntity> saved = captor.getAllValues();

        assertThat(saved.get(0).getSequence()).isZero();
        assertThat(saved.get(0).getStepName()).isEqualTo(OffboardingStepExecutor.SESSION_REVOCATION_STEP);
        assertThat(saved.get(1).getSequence()).isEqualTo(1);
        assertThat(saved.get(1).getStepName()).isEqualTo("TASK_B");
        assertThat(saved.get(2).getSequence()).isEqualTo(2);
        assertThat(saved.get(2).getStepName()).isEqualTo("TASK_A");
    }

    @Test
    void initiateRegistersAnAfterCommitSynchronization() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID initiatedBy = UUID.randomUUID();

        when(jobRepository.saveAndFlush(any())).thenAnswer(inv -> {
            OffboardingJobEntity job = inv.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });

        OffboardingServiceImpl service = new OffboardingServiceImpl(
                jobRepository, stepRepository, List.of(), stepExecutor);

        service.initiate(tenantId, userId, initiatedBy, "left the company");

        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
    }

    @Test
    void initiateSkipsSynchronizationRegistrationWhenNoneIsActive() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID initiatedBy = UUID.randomUUID();

        // No synchronization active for this test, unlike @BeforeEach's initSynchronization().
        TransactionSynchronizationManager.clearSynchronization();

        when(jobRepository.saveAndFlush(any())).thenAnswer(inv -> {
            OffboardingJobEntity job = inv.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });

        OffboardingServiceImpl service = new OffboardingServiceImpl(
                jobRepository, stepRepository, List.of(), stepExecutor);

        OffboardingJobEntity job = service.initiate(tenantId, userId, initiatedBy, "left the company");

        assertThat(job.getId()).isNotNull();
        assertThat(job.getTenantId()).isEqualTo(tenantId);
        assertThat(job.getUserId()).isEqualTo(userId);
        assertThat(job.getInitiatedBy()).isEqualTo(initiatedBy);
        assertThat(job.getReason()).isEqualTo("left the company");
    }
}
