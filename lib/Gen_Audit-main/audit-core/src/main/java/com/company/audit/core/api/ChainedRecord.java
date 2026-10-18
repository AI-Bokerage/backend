package com.company.audit.core.api;

import java.time.Instant;
import java.util.Objects;

/**
 * A single event as recorded in the hash chain, together with its position and linkage hashes.
 *
 * <p>No manual {@code equals}/{@code hashCode} override is needed: because {@link HashValue}
 * implements value-based equality over its wrapped bytes, the generated record equality is
 * already correct.
 *
 * @param event the audited event this record represents
 * @param seq the 1-based sequence number of this record within its partition
 * @param prevEventHash the event hash of the preceding record in this partition, or the
 *     partition's genesis hash if this is the first record
 * @param payloadHash the hash of this event's canonicalized payload
 * @param eventHash the hash linking {@code prevEventHash} and {@code payloadHash}, forming this
 *     record's contribution to the chain
 * @param recordedAt the instant at which this record was appended to the ledger
 */
public record ChainedRecord(
        AuditEvent event,
        long seq,
        HashValue prevEventHash,
        HashValue payloadHash,
        HashValue eventHash,
        Instant recordedAt) {

    /**
     * Validates that all fields are present.
     */
    public ChainedRecord {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(prevEventHash, "prevEventHash must not be null");
        Objects.requireNonNull(payloadHash, "payloadHash must not be null");
        Objects.requireNonNull(eventHash, "eventHash must not be null");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null");
    }
}
