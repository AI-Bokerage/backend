package com.company.audit.spring.metrics;

import java.time.Duration;

/**
 * The single metrics-recording seam for every call site in this starter that plausibly wants to
 * observe outcomes and timing: message consumption, chain append, scheduled verification, and
 * anchoring.
 *
 * <p>This is not a competing metrics system — it is still backed by Micrometer when Micrometer
 * is present (see {@link MicrometerAuditMetricsRecorder}) — it exists only because of a class
 * loading constraint discovered in an earlier phase: a type reference in an unconditionally
 * loaded class's own signature (a field, constructor parameter, or method parameter/return type)
 * is resolved eagerly by the JVM the moment something reflects on that class (e.g.
 * {@code getDeclaredFields()}/{@code getDeclaredMethods()}, which any unrelated
 * {@code BeanPostProcessor} can trigger), regardless of whether any {@code @Conditional}
 * annotation would otherwise have prevented that bean from ever being created. That means
 * {@code io.micrometer.core.instrument.MeterRegistry} can never appear directly in the signature
 * of {@link com.company.audit.spring.messaging.RabbitEventConsumer},
 * {@link com.company.audit.spring.persistence.jpa.adapter.JpaChainRepository},
 * {@link com.company.audit.spring.verification.ChainVerifierJob}, or
 * {@link com.company.audit.spring.scheduling.AnchorPublisherJob} — all four are loaded
 * unconditionally whenever their own auto-configuration activates, independent of whether
 * Micrometer happens to be on the classpath. This interface is the decoupling seam that
 * necessity forces regardless: those four classes depend only on this plain interface, and only
 * {@link MicrometerAuditMetricsRecorder} — never loaded unless
 * {@code @ConditionalOnClass(MeterRegistry.class)} matches — is permitted to name
 * {@code MeterRegistry} anywhere in its own signature.
 */
public interface AuditMetricsRecorder {

    /**
     * Records the outcome of consuming one inbound message.
     *
     * @param success {@code true} if the message was recorded successfully
     * @param duration how long handling the message took
     */
    void recordMessageConsumed(boolean success, Duration duration);

    /**
     * Records the outcome of appending one record to the tamper-evident ledger.
     *
     * @param success {@code true} if the append succeeded
     * @param duration how long the append took
     */
    void recordAppend(boolean success, Duration duration);

    /**
     * Records the outcome of one scheduled (or manually triggered) verification run across every
     * known partition.
     *
     * @param cleanCount how many partitions verified as {@code OK}
     * @param brokenCount how many partitions did not verify as {@code OK}
     * @param duration how long the run took
     */
    void recordVerification(int cleanCount, int brokenCount, Duration duration);

    /**
     * Records the outcome of one scheduled (or manually triggered) anchor-publishing run across
     * every known partition.
     *
     * @param successCount how many partitions anchored successfully
     * @param failureCount how many partitions failed to anchor
     * @param duration how long the run took
     */
    void recordAnchor(int successCount, int failureCount, Duration duration);

    /**
     * Records how long a single {@code append()} call waited to acquire a partition's Postgres
     * advisory lock, introduced in Phase 8's distributed-ordering mechanism.
     *
     * @param waitDuration how long the wait took
     */
    void recordPartitionLockWait(Duration waitDuration);

    /**
     * Records that an {@code append()} call was rejected because the caller's tip read had gone
     * stale by the time the partition's advisory lock was acquired — see
     * {@code JpaChainRepository}'s Javadoc for why this check exists in addition to the lock
     * itself.
     *
     * @param partitionKey the partition whose caller was rejected
     */
    void recordStalenessRejection(String partitionKey);

    /**
     * Records that an {@code append()} call failed because it could not acquire a partition's
     * advisory lock within the configured {@code audit.jpa.partition-lock-timeout}.
     *
     * @param partitionKey the partition whose lock could not be acquired in time
     */
    void recordLockTimeout(String partitionKey);

    /**
     * Records that an inbound message's partition is owned by this instance, per
     * {@code PartitionShardResolver} — the accepted side of the shard-ownership filter, recorded
     * alongside {@link #recordShardMismatch} so an operator can compute an ownership ratio rather
     * than only ever seeing rejections.
     *
     * @param partitionKey the partition this instance accepted
     */
    void recordShardOwnershipAccepted(String partitionKey);

    /**
     * Records that an inbound message's partition is <em>not</em> owned by this instance, per
     * {@code PartitionShardResolver} — the message is not processed at all when this happens.
     *
     * @param partitionKey the partition this instance rejected
     */
    void recordShardMismatch(String partitionKey);
}
