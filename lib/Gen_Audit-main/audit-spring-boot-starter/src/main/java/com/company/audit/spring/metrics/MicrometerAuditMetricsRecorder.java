package com.company.audit.spring.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;

/**
 * The only class in this entire starter permitted to name {@link MeterRegistry} in its own
 * signature — see {@link AuditMetricsRecorder}'s Javadoc for why every other call site is
 * forbidden from doing so. Instantiated only from
 * {@code com.company.audit.spring.autoconfigure.AuditMetricsAutoConfiguration}'s nested
 * {@code @ConditionalOnClass(MeterRegistry.class)} configuration, so this class's bytecode is
 * never even loaded when Micrometer is genuinely absent from the classpath.
 */
public class MicrometerAuditMetricsRecorder implements AuditMetricsRecorder {

    private final MeterRegistry meterRegistry;

    /**
     * Creates a new recorder.
     *
     * @param meterRegistry the registry to record every metric to
     */
    public MicrometerAuditMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordMessageConsumed(boolean success, Duration duration) {
        String outcome = success ? "success" : "failure";
        meterRegistry.counter("audit.messaging.consumed", "outcome", outcome).increment();
        meterRegistry.timer("audit.messaging.consumed.duration", "outcome", outcome).record(duration);
    }

    @Override
    public void recordAppend(boolean success, Duration duration) {
        String outcome = success ? "success" : "failure";
        meterRegistry.counter("audit.ledger.append", "outcome", outcome).increment();
        meterRegistry.timer("audit.ledger.append.duration", "outcome", outcome).record(duration);
    }

    @Override
    public void recordVerification(int cleanCount, int brokenCount, Duration duration) {
        meterRegistry.counter("audit.verification.partitions", "status", "clean").increment(cleanCount);
        meterRegistry.counter("audit.verification.partitions", "status", "broken").increment(brokenCount);
        meterRegistry.timer("audit.verification.duration").record(duration);
    }

    @Override
    public void recordAnchor(int successCount, int failureCount, Duration duration) {
        meterRegistry.counter("audit.anchor.partitions", "status", "success").increment(successCount);
        meterRegistry.counter("audit.anchor.partitions", "status", "failure").increment(failureCount);
        meterRegistry.timer("audit.anchor.duration").record(duration);
    }

    @Override
    public void recordPartitionLockWait(Duration waitDuration) {
        meterRegistry.timer("audit.jpa.partition-lock.wait").record(waitDuration);
    }

    @Override
    public void recordStalenessRejection(String partitionKey) {
        // partitionKey is deliberately not a tag here: partition keys are effectively unbounded
        // cardinality, and Micrometer's own guidance is that tag values must come from a small,
        // bounded set — the DEBUG log line (see JpaChainRepository) is where partitionKey-level
        // detail belongs, not a metric dimension.
        meterRegistry.counter("audit.jpa.partition-lock.staleness-rejections").increment();
    }

    @Override
    public void recordLockTimeout(String partitionKey) {
        meterRegistry.counter("audit.jpa.partition-lock.timeouts").increment();
    }

    @Override
    public void recordShardOwnershipAccepted(String partitionKey) {
        // partitionKey deliberately not a tag — same unbounded-cardinality reasoning as the
        // staleness/timeout counters above.
        meterRegistry.counter("audit.rabbit.shard.ownership", "outcome", "accepted").increment();
    }

    @Override
    public void recordShardMismatch(String partitionKey) {
        meterRegistry.counter("audit.rabbit.shard.ownership", "outcome", "mismatch").increment();
    }
}
