package com.company.audit.spring.anchor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * The outcome of a single anchor-publishing run across every known partition.
 *
 * <p>Keyed by {@code partitionKey}, same shape and same reasoning as {@code VerificationSummary}
 * from the scheduled-verification phase: "what happened for partition X" is the dominant
 * question, not "iterate everything."
 *
 * @param storageReferenceByPartition the opaque storage reference returned for each partition
 *     that anchored successfully, keyed by partition key
 * @param failuresByPartition the exception that caused each partition's anchor publish to fail,
 *     keyed by partition key
 * @param startedAt the instant the run started
 * @param finishedAt the instant the run finished
 */
public record AnchorSummary(
        Map<String, String> storageReferenceByPartition,
        Map<String, Exception> failuresByPartition,
        Instant startedAt,
        Instant finishedAt) {

    /**
     * Returns how long the run took.
     *
     * @return the duration between {@link #startedAt} and {@link #finishedAt}
     */
    public Duration duration() {
        return Duration.between(startedAt, finishedAt);
    }

    /**
     * Returns how many partitions anchored successfully.
     *
     * @return the count of successfully anchored partitions
     */
    public int successCount() {
        return storageReferenceByPartition.size();
    }

    /**
     * Returns how many partitions failed to anchor.
     *
     * @return the count of failed partitions
     */
    public int failureCount() {
        return failuresByPartition.size();
    }
}
