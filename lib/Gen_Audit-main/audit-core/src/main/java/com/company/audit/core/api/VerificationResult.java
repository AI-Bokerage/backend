package com.company.audit.core.api;

import com.company.audit.core.api.enums.AuditChainStatus;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of verifying a partition's hash chain.
 *
 * @param partitionKey the key identifying the partition that was verified
 * @param status the outcome status of the verification
 * @param breakAtSeq the sequence number at which a break was detected, empty if none
 * @param detail a human-readable description of the outcome, or {@code null} if none
 */
public record VerificationResult(
        String partitionKey, AuditChainStatus status, Optional<Long> breakAtSeq, String detail) {

    /**
     * Validates that the required fields are present.
     */
    public VerificationResult {
        Objects.requireNonNull(partitionKey, "partitionKey must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(breakAtSeq, "breakAtSeq must not be null");
    }

    /**
     * Creates a successful verification result for the given partition.
     *
     * @param partitionKey the key identifying the verified partition
     * @return a result with status {@link AuditChainStatus#OK} and no break sequence
     */
    public static VerificationResult ok(String partitionKey) {
        return new VerificationResult(partitionKey, AuditChainStatus.OK, Optional.empty(), null);
    }

    /**
     * Creates a failed verification result reporting where the chain broke.
     *
     * @param partitionKey the key identifying the verified partition
     * @param status the specific failure status
     * @param seq the sequence number at which the break was detected
     * @param detail a human-readable description of the break
     * @return a result describing the detected break
     */
    public static VerificationResult brokenAt(
            String partitionKey, AuditChainStatus status, long seq, String detail) {
        return new VerificationResult(partitionKey, status, Optional.of(seq), detail);
    }
}
