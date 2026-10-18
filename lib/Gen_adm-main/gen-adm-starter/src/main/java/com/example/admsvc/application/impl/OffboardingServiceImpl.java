package com.example.admsvc.application.impl;

import com.example.admsvc.application.service.OffboardingService;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.OffboardingJobStatus;
import com.example.admsvc.domain.port.OffboardingStepHandler;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingStepEntity;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingJobRepository;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

@Service
public class OffboardingServiceImpl implements OffboardingService {

    private final OffboardingJobRepository jobRepository;
    private final OffboardingStepRepository stepRepository;
    private final List<OffboardingStepHandler> stepHandlers;
    private final OffboardingStepExecutor stepExecutor;

    public OffboardingServiceImpl(OffboardingJobRepository jobRepository,
                                   OffboardingStepRepository stepRepository,
                                   List<OffboardingStepHandler> stepHandlers,
                                   OffboardingStepExecutor stepExecutor) {
        this.jobRepository = jobRepository;
        this.stepRepository = stepRepository;
        this.stepHandlers = stepHandlers;
        this.stepExecutor = stepExecutor;
    }

    @Override
    @Transactional
    public OffboardingJobEntity initiate(UUID tenantId, UUID userId, UUID initiatedBy, String reason) {
        OffboardingJobEntity job = jobRepository.saveAndFlush(OffboardingJobEntity.builder()
                .tenantId(tenantId)
                .userId(userId)
                .initiatedBy(initiatedBy)
                .reason(reason)
                .build());

        stepRepository.save(OffboardingStepEntity.builder()
                .jobId(job.getId())
                .tenantId(tenantId)
                .userId(userId)
                .sequence(0)
                .stepName(OffboardingStepExecutor.SESSION_REVOCATION_STEP)
                .build());

        int sequence = 1;
        for (OffboardingStepHandler handler : stepHandlers) {
            stepRepository.save(OffboardingStepEntity.builder()
                    .jobId(job.getId())
                    .tenantId(tenantId)
                    .userId(userId)
                    .sequence(sequence++)
                    .stepName(handler.stepName())
                    .build());
        }

        UUID jobId = job.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    stepExecutor.execute(tenantId, jobId);
                }
            });
        }
        return job;
    }

    @Override
    @Transactional(readOnly = true)
    public OffboardingJobEntity getJob(UUID tenantId, UUID jobId) {
        return findJobOrThrow(tenantId, jobId);
    }

    @Override
    @Transactional
    public OffboardingJobEntity retry(UUID tenantId, UUID jobId) {
        OffboardingJobEntity job = findJobOrThrow(tenantId, jobId);
        if (job.getStatus() == OffboardingJobStatus.COMPLETED
                || job.getStatus() == OffboardingJobStatus.PERMANENTLY_FAILED) {
            return job;
        }
        stepExecutor.execute(tenantId, jobId);
        return findJobOrThrow(tenantId, jobId);
    }

    private OffboardingJobEntity findJobOrThrow(UUID tenantId, UUID jobId) {
        return jobRepository.findByIdAndTenantId(jobId, tenantId)
                .orElseThrow(() -> new GenAdmNotFoundException("Offboarding job not found: " + jobId));
    }
}
