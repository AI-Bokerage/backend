package com.company.audit.spring.verification;

import com.company.audit.core.api.VerificationResult;
import com.company.audit.core.api.enums.AuditChainStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * The outcome of a single verification run across every known partition.
 *
 * <p>Keyed by {@code partitionKey} rather than a {@code List}: the dominant question a caller
 * asks of this summary is "what's the result for partition X," not "iterate everything," and a
 * map rules out duplicate entries for the same partition by construction.
 *
 * @param resultsByPartition each verified partition's result, keyed by partition key
 * @param startedAt the instant the run started
 * @param finishedAt the instant the run finished
 */
public record VerificationSummary(
        Map<String, VerificationResult> resultsByPartition, Instant startedAt, Instant finishedAt) {

    /**
     * Returns how long the run took.
     *
     * @return the duration between {@link #startedAt} and {@link #finishedAt}
     */
    public Duration duration() {
        return Duration.between(startedAt, finishedAt);
    }

    /**
     * Returns how many partitions verified as {@link AuditChainStatus#OK}.
     *
     * @return the count of clean partitions
     */
    public long cleanCount() {
        return resultsByPartition.values().stream()
                .filter(result -> result.status() == AuditChainStatus.OK)
                .count();
    }

    /**
     * Returns how many partitions did not verify as {@link AuditChainStatus#OK}.
     *
     * @return the count of broken partitions
     */
    public long brokenCount() {
        return resultsByPartition.values().stream()
                .filter(result -> result.status() != AuditChainStatus.OK)
                .count();
    }
}
