// gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/ProvisioningSagaOrchestrator.java
package com.example.tnt_svc.saga;

import com.example.tnt_svc.domain.ProvisioningJob;
import com.example.tnt_svc.domain.ProvisioningJobStatus;
import com.example.tnt_svc.domain.ProvisioningStep;
import com.example.tnt_svc.domain.ProvisioningStepStatus;
import com.example.tnt_svc.domain.Tenant;
import com.example.tnt_svc.domain.TenantStatus;
import com.example.tnt_svc.domain.exception.ProvisioningJobNotFoundException;
import com.example.tnt_svc.domain.exception.ProvisioningLockException;
import com.example.tnt_svc.domain.exception.TenantNotFoundException;
import com.example.tnt_svc.persistence.ProvisioningJobRepository;
import com.example.tnt_svc.persistence.ProvisioningStepRepository;
import com.example.tnt_svc.persistence.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class ProvisioningSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningSagaOrchestrator.class);
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final TenantRepository tenantRepository;
    private final ProvisioningJobRepository jobRepository;
    private final ProvisioningStepRepository stepRepository;
    private final ProvisioningStepsProperties stepsProperties;
    private final StepWebhookClient webhookClient;
    private final ProvisioningLockService lockService;

    public ProvisioningSagaOrchestrator(
        TenantRepository tenantRepository,
        ProvisioningJobRepository jobRepository,
        ProvisioningStepRepository stepRepository,
        ProvisioningStepsProperties stepsProperties,
        StepWebhookClient webhookClient,
        ProvisioningLockService lockService
    ) {
        this.tenantRepository = tenantRepository;
        this.jobRepository = jobRepository;
        this.stepRepository = stepRepository;
        this.stepsProperties = stepsProperties;
        this.webhookClient = webhookClient;
        this.lockService = lockService;
    }

    public UUID startProvisioning(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new TenantNotFoundException(tenantId));

        ProvisioningJob job = ProvisioningJob.builder()
            .tenantId(tenantId)
            .status(ProvisioningJobStatus.PENDING)
            .retryCount(0)
            .maxRetries(stepsProperties.getMaxRetries())
            .callbackToken(UUID.randomUUID().toString())
            .context(new HashMap<>())
            .expiresAt(Instant.now().plus(Duration.ofMinutes(stepsProperties.getTimeoutMinutes())))
            .build();
        job = jobRepository.save(job);

        int order = 0;
        for (ProvisioningStepDefinition def : stepsProperties.getSteps()) {
            stepRepository.save(ProvisioningStep.builder()
                .jobId(job.getId())
                .stepName(def.name())
                .stepOrder(order++)
                .status(ProvisioningStepStatus.PENDING)
                .build());
        }

        tenant.setProvisioningJobId(job.getId());
        tenantRepository.save(tenant);

        job.markInProgress();
        jobRepository.save(job);

        driveNextStep(job.getId());
        return job.getId();
    }

    public void driveNextStep(UUID jobId) {
        ProvisioningJob job = jobRepository.findById(jobId).orElseThrow(() -> new ProvisioningJobNotFoundException(jobId));
        List<ProvisioningStep> steps = stepRepository.findByJobIdOrderByStepOrderAsc(jobId);

        Optional<ProvisioningStep> next = steps.stream()
            .filter(s -> s.getStatus() != ProvisioningStepStatus.COMPLETED)
            .findFirst();

        if (next.isEmpty()) {
            completeJob(job);
            return;
        }

        ProvisioningStep step = next.get();
        ProvisioningStepDefinition definition = findDefinition(step.getStepName());

        Optional<String> lockToken = lockService.tryLock(job.getTenantId(), LOCK_TTL);
        if (lockToken.isEmpty()) {
            log.debug("Skipping step drive, tenant lock already held: tenantId={}", job.getTenantId());
            return;
        }

        try {
            step.markInProgress();
            stepRepository.save(step);

            Map<String, Object> payload = new HashMap<>(job.getContext());
            payload.put("tenantId", job.getTenantId());
            payload.put("jobId", job.getId());
            payload.put("stepName", step.getStepName());
            if (definition.mode() == StepMode.ASYNC) {
                payload.put("callbackToken", job.getCallbackToken());
            }

            StepCallResult result = webhookClient.call(definition, payload);

            if (!result.success()) {
                step.markFailed(result.error());
                stepRepository.save(step);
                job.markFailed("Step " + step.getStepName() + " failed: " + result.error());
                jobRepository.save(job);
                return;
            }

            if (definition.mode() == StepMode.SYNC) {
                step.markCompleted();
                stepRepository.save(step);
                job.mergeContext(result.context());
                jobRepository.save(job);
                lockService.unlock(job.getTenantId(), lockToken.get());
                driveNextStep(jobId);
                return;
            }
            // async: stays IN_PROGRESS, real completion arrives via handleCallback
        } finally {
            lockService.unlock(job.getTenantId(), lockToken.get());
        }
    }

    public void handleCallback(UUID jobId, String stepName, String token, boolean success, Map<String, Object> context, String error) {
        ProvisioningJob job = jobRepository.findById(jobId).orElseThrow(() -> new ProvisioningJobNotFoundException(jobId));

        Optional<String> lockToken = lockService.tryLock(job.getTenantId(), LOCK_TTL);
        if (lockToken.isEmpty()) {
            // At-least-once delivery: signal the caller (409) so it retries instead of
            // dropping the callback and hanging the step until timeout.
            log.warn("Callback could not acquire tenant lock, signalling retry: tenantId={} jobId={}", job.getTenantId(), jobId);
            throw new ProvisioningLockException(job.getTenantId());
        }

        boolean advance = false;
        try {
            if (job.getStatus() != ProvisioningJobStatus.IN_PROGRESS) {
                // Terminal-state guard: a DEAD/COMPLETED/FAILED job must not be resurrected
                // by a late or duplicated async callback (would wrongly activate a rolled-back tenant).
                log.warn("Rejected provisioning callback for job not IN_PROGRESS (status={}): jobId={} stepName={}",
                    job.getStatus(), jobId, stepName);
                return;
            }

            if (!job.getCallbackToken().equals(token)) {
                log.warn("Rejected provisioning callback with mismatched token: jobId={} stepName={}", jobId, stepName);
                return;
            }

            ProvisioningStep step = stepRepository.findByJobIdAndStepName(jobId, stepName).orElse(null);
            if (step == null || step.getStatus() != ProvisioningStepStatus.IN_PROGRESS) {
                log.warn("Rejected provisioning callback for unknown/non-in-progress step: jobId={} stepName={}", jobId, stepName);
                return;
            }

            if (success) {
                step.markCompleted();
                stepRepository.save(step);
                job.mergeContext(context == null ? Map.of() : context);
                jobRepository.save(job);
                advance = true;
            } else {
                step.markFailed(error);
                stepRepository.save(step);
                job.markFailed("Step " + stepName + " failed: " + error);
                jobRepository.save(job);
            }
        } finally {
            lockService.unlock(job.getTenantId(), lockToken.get());
        }

        if (advance) {
            driveNextStep(jobId);
        }
    }

    public void retryProvisioning(UUID jobId) {
        ProvisioningJob job = jobRepository.findById(jobId).orElseThrow(() -> new ProvisioningJobNotFoundException(jobId));

        Optional<String> lockToken = lockService.tryLock(job.getTenantId(), LOCK_TTL);
        if (lockToken.isEmpty()) {
            log.debug("Skipping retry, tenant lock already held: tenantId={}", job.getTenantId());
            return;
        }

        try {
            if (job.getStatus() != ProvisioningJobStatus.FAILED) {
                // Already moved off FAILED by another caller (manual retry, another scheduler
                // tick, etc.) since this caller's snapshot was taken. Nothing to do here —
                // must NOT markDead() a job that isn't actually FAILED.
                log.debug("Skipping retry, job no longer FAILED (status={}): jobId={}", job.getStatus(), jobId);
                return;
            }

            if (!job.canRetry()) {
                job.markDead();
                jobRepository.save(job);
                return;
            }

            job.incrementRetry();
            job.markInProgress();
            // Extend the timeout window so the just-retried job isn't immediately reaped
            // by ProvisioningTimeoutScheduler on its next tick.
            job.setExpiresAt(Instant.now().plus(Duration.ofMinutes(stepsProperties.getTimeoutMinutes())));
            jobRepository.save(job);

            stepRepository.findByJobIdOrderByStepOrderAsc(jobId).stream()
                .filter(s -> s.getStatus() == ProvisioningStepStatus.FAILED)
                .forEach(s -> {
                    s.resetToPending();
                    stepRepository.save(s);
                });
        } finally {
            lockService.unlock(job.getTenantId(), lockToken.get());
        }

        driveNextStep(jobId);
    }

    public void handleTimeout(UUID jobId) {
        ProvisioningJob job = jobRepository.findById(jobId).orElseThrow(() -> new ProvisioningJobNotFoundException(jobId));

        Optional<String> lockToken = lockService.tryLock(job.getTenantId(), LOCK_TTL);
        if (lockToken.isEmpty()) {
            log.debug("Skipping timeout handling, tenant lock already held: tenantId={}", job.getTenantId());
            return;
        }

        try {
            List<ProvisioningStep> completedStepsReversed = stepRepository.findByJobIdOrderByStepOrderAsc(jobId).stream()
                .filter(s -> s.getStatus() == ProvisioningStepStatus.COMPLETED)
                .sorted((a, b) -> Integer.compare(b.getStepOrder(), a.getStepOrder()))
                .toList();

            for (ProvisioningStep step : completedStepsReversed) {
                ProvisioningStepDefinition definition = findDefinition(step.getStepName());
                Map<String, Object> payload = new HashMap<>(job.getContext());
                payload.put("tenantId", job.getTenantId());
                payload.put("jobId", job.getId());
                payload.put("stepName", step.getStepName());
                webhookClient.compensate(definition, payload);
            }

            job.markDead();
            jobRepository.save(job);
        } finally {
            lockService.unlock(job.getTenantId(), lockToken.get());
        }
    }

    private void completeJob(ProvisioningJob job) {
        job.markCompleted();
        jobRepository.save(job);

        Tenant tenant = tenantRepository.findById(job.getTenantId()).orElseThrow(() -> new TenantNotFoundException(job.getTenantId()));
        if (tenant.getStatus() != TenantStatus.PROVISIONING) {
            // Tenant left PROVISIONING while the job ran (e.g. operator cancelled it) — a legal
            // transition. The cancellation wins; don't throw InvalidTenantStateTransitionException.
            log.info("Tenant no longer PROVISIONING (status={}), skipping activation: tenantId={} jobId={}",
                tenant.getStatus(), tenant.getId(), job.getId());
            return;
        }
        tenant.activateAfterProvisioning();
        tenantRepository.save(tenant);
    }

    private ProvisioningStepDefinition findDefinition(String stepName) {
        return stepsProperties.getSteps().stream()
            .filter(d -> d.name().equals(stepName))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No step definition configured for: " + stepName));
    }
}
