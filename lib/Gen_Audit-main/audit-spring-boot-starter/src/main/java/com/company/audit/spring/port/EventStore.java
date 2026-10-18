package com.company.audit.spring.port;

import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.ChainedRecord;
import java.util.List;

/**
 * A driven port for durable storage of the full, rich audit event — including its payload,
 * which the tamper-evident Postgres ledger ({@code ChainRepository}/{@code audit_immutable})
 * deliberately never stores.
 *
 * <p>Structurally parallel to {@code audit-core}'s own {@code ChainRepository}: this is the
 * interface, {@code MongoEventStore} is the one adapter implementing it today, and
 * {@link com.company.audit.spring.ingestion.AuditRecorder} (the orchestrator) depends on this
 * port, never on the adapter directly.
 */
public interface EventStore {

    /**
     * Persists the full event alongside its resulting chained record's linkage metadata (its
     * sequence number and hashes), so a rich copy can be cross-checked against the ledger.
     *
     * @param event the full audit event, including its payload
     * @param record the chained record produced by appending {@code event} to the ledger
     */
    void save(AuditEvent event, ChainedRecord record);

    /**
     * Returns all rich events previously saved for the given partition.
     *
     * @param partitionKey the partition key to look up
     * @return the partition's saved events, including their payloads
     */
    List<AuditEvent> findByPartitionKey(String partitionKey);
}
