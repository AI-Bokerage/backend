package com.company.audit.spring.metrics;

import java.time.Duration;

/**
 * An {@link AuditMetricsRecorder} that records nothing.
 *
 * <p>The always-available default: registered whenever a consuming application hasn't supplied
 * its own {@link AuditMetricsRecorder} bean and Micrometer isn't on the classpath (or is, but
 * has no {@code MeterRegistry} bean) — see
 * {@code com.company.audit.spring.autoconfigure.AuditMetricsAutoConfiguration}.
 */
public class NoOpAuditMetricsRecorder implements AuditMetricsRecorder {

    /**
     * Creates a new no-op recorder.
     */
    public NoOpAuditMetricsRecorder() {
    }

    @Override
    public void recordMessageConsumed(boolean success, Duration duration) {
        // intentionally does nothing
    }

    @Override
    public void recordAppend(boolean success, Duration duration) {
        // intentionally does nothing
    }

    @Override
    public void recordVerification(int cleanCount, int brokenCount, Duration duration) {
        // intentionally does nothing
    }

    @Override
    public void recordAnchor(int successCount, int failureCount, Duration duration) {
        // intentionally does nothing
    }

    @Override
    public void recordPartitionLockWait(Duration waitDuration) {
        // intentionally does nothing
    }

    @Override
    public void recordStalenessRejection(String partitionKey) {
        // intentionally does nothing
    }

    @Override
    public void recordLockTimeout(String partitionKey) {
        // intentionally does nothing
    }

    @Override
    public void recordShardOwnershipAccepted(String partitionKey) {
        // intentionally does nothing
    }

    @Override
    public void recordShardMismatch(String partitionKey) {
        // intentionally does nothing
    }
}
