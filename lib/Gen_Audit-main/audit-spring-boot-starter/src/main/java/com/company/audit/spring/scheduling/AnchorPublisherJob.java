package com.company.audit.spring.scheduling;

import com.company.audit.core.api.PartitionContext;
import com.company.audit.spring.anchor.AnchorPublishFailedEvent;
import com.company.audit.spring.anchor.AnchorPublisher;
import com.company.audit.spring.anchor.AnchorSummary;
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
 * Publishes an anchor for every known partition on a schedule.
 *
 * <p>Reuses {@link PartitionCatalog} from the scheduled-verification phase rather than
 * introducing a second enumeration mechanism.
 *
 * <p><b>Runs sequentially, partition by partition, by design — not an oversight.</b> Same
 * reasoning as {@code ChainVerifierJob}: do not parallelize this loop without first thinking
 * through ordering, the resulting concurrent load on the underlying object storage and database,
 * and the operational impact of many simultaneous anchor publishes landing at once.
 *
 * <p>A single partition's anchor-publish failure does not abort the run for any other partition:
 * each partition's {@link AnchorPublisher#publish} call is individually guarded, its failure
 * recorded in the returned {@link AnchorSummary} and published as an
 * {@link AnchorPublishFailedEvent}, and the loop continues.
 *
 * <p>Keeps the most recent {@link AnchorSummary} in an {@link AtomicReference}, updated at the
 * end of every {@link #runNow()} call (scheduled or manual) — read by
 * {@code com.company.audit.spring.health.AuditChainHealthIndicator} for the timestamp of the last
 * successful anchoring run. The health indicator never calls {@link #runNow()} itself.
 */
public class AnchorPublisherJob {

    private final PartitionCatalog partitionCatalog;
    private final AnchorPublisher anchorPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditMetricsRecorder metricsRecorder;
    private final Clock clock;
    private final AtomicReference<AnchorSummary> lastSummary = new AtomicReference<>();

    /**
     * Creates a new job.
     *
     * @param partitionCatalog enumerates every known partition
     * @param anchorPublisher publishes a single partition's anchor
     * @param eventPublisher publishes {@link AnchorPublishFailedEvent} on a per-partition failure
     * @param metricsRecorder records anchoring outcomes and timing
     * @param clock the clock used to timestamp the run and any detected failures
     */
    public AnchorPublisherJob(
            PartitionCatalog partitionCatalog,
            AnchorPublisher anchorPublisher,
            ApplicationEventPublisher eventPublisher,
            AuditMetricsRecorder metricsRecorder,
            Clock clock) {
        this.partitionCatalog = partitionCatalog;
        this.anchorPublisher = anchorPublisher;
        this.eventPublisher = eventPublisher;
        this.metricsRecorder = metricsRecorder;
        this.clock = clock;
    }

    /**
     * The scheduled entry point. Delegates to {@link #runNow()} — see that method for the actual
     * anchor-publishing logic.
     */
    @Scheduled(cron = "${audit.anchor.publish-cron:0 0 3 * * *}")
    public void runScheduled() {
        runNow();
    }

    /**
     * Publishes an anchor for every known partition sequentially. A partition whose publish
     * fails is recorded as a failure and does not stop the run for any other partition.
     *
     * <p>Exposed as a plain callable method, not only reachable via {@link #runScheduled()}, so
     * tests and {@code audit-demo} can trigger a run without waiting for the cron schedule.
     *
     * @return a summary of the run
     */
    public AnchorSummary runNow() {
        Instant startedAt = clock.instant();
        Map<String, String> storageReferenceByPartition = new LinkedHashMap<>();
        Map<String, Exception> failuresByPartition = new LinkedHashMap<>();

        for (PartitionContext partitionContext : partitionCatalog.listAll()) {
            String partitionKey = partitionContext.partitionKey();
            try {
                String storageReference = anchorPublisher.publish(partitionKey);
                storageReferenceByPartition.put(partitionKey, storageReference);
            } catch (Exception e) {
                failuresByPartition.put(partitionKey, e);
                eventPublisher.publishEvent(new AnchorPublishFailedEvent(partitionKey, e, clock.instant()));
            }
        }

        AnchorSummary summary =
                new AnchorSummary(storageReferenceByPartition, failuresByPartition, startedAt, clock.instant());
        metricsRecorder.recordAnchor(summary.successCount(), summary.failureCount(), summary.duration());
        lastSummary.set(summary);
        return summary;
    }

    /**
     * Returns the most recent run's summary, or {@code null} if no run has completed yet.
     *
     * @return the last completed run's summary, or {@code null}
     */
    public AnchorSummary lastSummary() {
        return lastSummary.get();
    }
}
