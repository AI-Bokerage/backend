package com.company.audit.spring.verification;

import com.company.audit.core.api.AuditVerifier;
import com.company.audit.core.api.PartitionContext;
import com.company.audit.core.api.VerificationResult;
import com.company.audit.core.api.enums.AuditChainStatus;
import com.company.audit.spring.metrics.AuditMetricsRecorder;
import com.company.audit.spring.partition.PartitionCatalog;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs {@link AuditVerifier} against every known partition on a schedule.
 *
 * <p>Depends only on {@link PartitionCatalog} and {@link AuditVerifier} — deliberately not on
 * {@code PartitionRegistry}. Enumerating partitions is a read-only catalog query; resolving one
 * is a lifecycle operation that may write a row on first call. A verification job has no business
 * invoking the latter just to iterate what already exists.
 *
 * <p><b>Verification runs sequentially, partition by partition. This is an intentional design
 * decision, not an oversight.</b> Do not parallelize this loop without first thinking through
 * ordering, the resulting concurrent load on the underlying database, and the operational impact
 * of many simultaneous full-chain scans against production data. If verification throughput ever
 * needs to improve, that's a deliberate follow-up decision to make with those trade-offs in mind,
 * not a default to reach for casually.
 *
 * <p>Keeps the most recent {@link VerificationSummary} in an {@link AtomicReference}, updated at
 * the end of every {@link #runNow()} call (scheduled or manual) — this is what
 * {@code com.company.audit.spring.health.AuditChainHealthIndicator} reads from. The health
 * indicator itself never calls {@link #runNow()}; it only ever reads this cached state, so that a
 * health check stays cheap regardless of how expensive a full chain walk is.
 */
public class ChainVerifierJob {

    private final PartitionCatalog partitionCatalog;
    private final AuditVerifier auditVerifier;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditMetricsRecorder metricsRecorder;
    private final Clock clock;
    private final AtomicReference<VerificationSummary> lastSummary = new AtomicReference<>();

    /**
     * Creates a new job.
     *
     * @param partitionCatalog enumerates every known partition
     * @param auditVerifier verifies a single partition's chain
     * @param eventPublisher publishes {@link AuditChainBreakDetectedEvent} when a chain doesn't
     *     verify as {@code OK}
     * @param metricsRecorder records verification outcomes and timing
     * @param clock the clock used to timestamp the run and any detected breaks
     */
    public ChainVerifierJob(
            PartitionCatalog partitionCatalog,
            AuditVerifier auditVerifier,
            ApplicationEventPublisher eventPublisher,
            AuditMetricsRecorder metricsRecorder,
            Clock clock) {
        this.partitionCatalog = partitionCatalog;
        this.auditVerifier = auditVerifier;
        this.eventPublisher = eventPublisher;
        this.metricsRecorder = metricsRecorder;
        this.clock = clock;
    }

    /**
     * The scheduled entry point. Delegates to {@link #runNow()} — see that method for the actual
     * verification logic.
     */
    @Scheduled(cron = "${audit.verification.cron:0 0 2 * * *}")
    public void runScheduled() {
        runNow();
    }

    /**
     * Verifies every known partition sequentially, publishing an
     * {@link AuditChainBreakDetectedEvent} for each one that doesn't verify as {@code OK}.
     *
     * <p>Exposed as a plain callable method, not only reachable via {@link #runScheduled()}, so
     * tests and {@code audit-demo} can trigger a run without waiting for the cron schedule.
     *
     * @return a summary of the run
     */
    public VerificationSummary runNow() {
        Instant startedAt = clock.instant();
        Map<String, VerificationResult> resultsByPartition = new LinkedHashMap<>();
        for (PartitionContext partitionContext : partitionCatalog.listAll()) {
            VerificationResult result = auditVerifier.verify(partitionContext.partitionKey(), partitionContext);
            resultsByPartition.put(partitionContext.partitionKey(), result);
            if (result.status() != AuditChainStatus.OK) {
                eventPublisher.publishEvent(
                        new AuditChainBreakDetectedEvent(partitionContext.partitionKey(), result, clock.instant()));
            }
        }
        VerificationSummary summary = new VerificationSummary(resultsByPartition, startedAt, clock.instant());
        metricsRecorder.recordVerification((int) summary.cleanCount(), (int) summary.brokenCount(), summary.duration());
        lastSummary.set(summary);
        return summary;
    }

    /**
     * Returns the most recent run's summary, or {@code null} if no run has completed yet.
     *
     * @return the last completed run's summary, or {@code null}
     */
    public VerificationSummary lastSummary() {
        return lastSummary.get();
    }
}
