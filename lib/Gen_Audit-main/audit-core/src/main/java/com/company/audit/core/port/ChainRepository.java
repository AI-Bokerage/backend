package com.company.audit.core.port;

import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.api.exception.SeqConflictException;
import java.util.List;
import java.util.Optional;

/**
 * A driven port for durable storage of a partition's hash-chained records.
 *
 * <p>Implementations back this with whatever storage technology the consuming application uses
 * (a relational database, for example); this library ships only in-memory test fixtures.
 */
public interface ChainRepository {

    /**
     * Returns the most recently appended record for the given partition, if any.
     *
     * @param partitionKey the key identifying the partition
     * @return the record with the highest {@code seq} in the partition, or empty if the
     *     partition has no records yet
     */
    Optional<ChainedRecord> findTip(String partitionKey);

    /**
     * Appends a record to its partition's chain.
     *
     * <p>Implementations must atomically reject a duplicate {@code (partitionKey, seq)} pair by
     * throwing {@link SeqConflictException} rather than overwriting or silently succeeding.
     *
     * @param record the record to append
     * @throws SeqConflictException if a record already exists for this record's
     *     {@code (partitionKey, seq)} pair
     */
    void append(ChainedRecord record) throws SeqConflictException;

    /**
     * Returns all records for the given partition in ascending {@code seq} order.
     *
     * <p>This method performs no gap-filling or validation of the sequence: detecting gaps or
     * duplicate sequence numbers is the responsibility of
     * {@link com.company.audit.core.api.AuditVerifier}, not this repository.
     *
     * @param partitionKey the key identifying the partition
     * @return all records for the partition, ordered by ascending {@code seq}
     */
    List<ChainedRecord> findAllOrderedBySeq(String partitionKey);
}
