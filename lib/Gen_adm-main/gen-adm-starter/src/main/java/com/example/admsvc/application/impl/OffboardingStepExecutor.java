package com.example.admsvc.application.impl;

import com.example.admsvc.domain.enums.OffboardingJobStatus;
import com.example.admsvc.domain.enums.OffboardingStepStatus;
import com.example.admsvc.domain.port.OffboardingEventPublisher;
import com.example.admsvc.domain.port.OffboardingStepHandler;
import com.example.admsvc.domain.port.SessionRevocationGateway;
import com.example.admsvc.domain.port.TenantIdParam;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingStepEntity;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingJobRepository;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resumable, retry-only offboarding-step engine. {@code execute} skips any
 * step already COMPLETED and stops at the first failure — there is no
 * separate "resume" method; re-invoking {@code execute} on a FAILED job (below
 * {@code maxAttempts}) IS the resume path. Runs with no {@code GenAdmPrincipal}
 * reliably in scope (called from an {@code afterCommit} callback or a retry
 * endpoint), so tenant context comes from an explicit {@code @TenantIdParam}
 * argument, exactly like Phase 1's {@code bootstrapTenant}.
 */
@Service
public class OffboardingStepExecutor {

    public static final String SESSION_REVOCATION_STEP = "SESSION_REVOCATION";

    private final OffboardingJobRepository jobRepository;
    private final OffboardingStepRepository stepRepository;
    private final SessionRevocationGateway sessionRevocationGateway;
    private final Map<String, OffboardingStepHandler> handlersByName;
    private final OffboardingEventPublisher eventPublisher;

    public OffboardingStepExecutor(OffboardingJobRepository jobRepository,
                                    OffboardingStepRepository stepRepository,
                                    SessionRevocationGateway sessionRevocationGateway,
                                    List<OffboardingStepHandler> stepHandlers,
                                    OffboardingEventPublisher eventPublisher) {
        this.jobRepository = jobRepository;
        this.stepRepository = stepRepository;
        this.sessionRevocationGateway = sessionRevocationGateway;
        this.eventPublisher = eventPublisher;
        this.handlersByName = new HashMap<>();
        for (OffboardingStepHandler handler : stepHandlers) {
            handlersByName.put(handler.stepName(), handler);
        }
    }

    @Transactional
    public void execute(@TenantIdParam UUID tenantId, UUID jobId) {
        OffboardingJobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Offboarding job not found: " + jobId));
        if (job.getStatus() == OffboardingJobStatus.COMPLETED
                || job.getStatus() == OffboardingJobStatus.PERMANENTLY_FAILED) {
            return;
        }

        List<OffboardingStepEntity> steps = stepRepository.findAllByJobIdOrderBySequenceAsc(jobId);
        for (OffboardingStepEntity step : steps) {
            if (step.getStatus() == OffboardingStepStatus.COMPLETED) {
                continue;
            }
            step.setAttemptNumber(step.getAttemptNumber() + 1);
            try {
                runStep(job, step);
            } catch (RuntimeException e) {
                failStep(job, step, e);
                return;
            }
            step.setStatus(OffboardingStepStatus.COMPLETED);
            stepRepository.save(step);
        }

        job.setStatus(OffboardingJobStatus.COMPLETED);
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);
        eventPublisher.onCompleted(job.getTenantId(), job.getUserId());
    }

    private void runStep(OffboardingJobEntity job, OffboardingStepEntity step) {
        if (step.getSequence() == 0) {
            sessionRevocationGateway.revoke(job.getTenantId(), job.getUserId());
            return;
        }
        OffboardingStepHandler handler = handlersByName.get(step.getStepName());
        if (handler == null) {
            throw new IllegalStateException("No OffboardingStepHandler registered for step: " + step.getStepName());
        }
        handler.handle(job.getTenantId(), job.getUserId(), job.getInitiatedBy());
    }

    private void failStep(OffboardingJobEntity job, OffboardingStepEntity step, RuntimeException e) {
        step.setStatus(OffboardingStepStatus.FAILED);
        step.setErrorMessage(e.getMessage());
        stepRepository.save(step);

        int attemptCount = job.getAttemptCount() + 1;
        job.setAttemptCount(attemptCount);
        if (attemptCount >= job.getMaxAttempts()) {
            job.setStatus(OffboardingJobStatus.PERMANENTLY_FAILED);
            job.setNextRetryAt(null);
        } else {
            job.setStatus(OffboardingJobStatus.FAILED);
            job.setNextRetryAt(Instant.now().plus(Duration.ofMinutes(Math.min(5L * attemptCount, 30L))));
        }
        jobRepository.save(job);
        eventPublisher.onFailed(job.getTenantId(), job.getUserId(), e.getMessage());
    }
}
