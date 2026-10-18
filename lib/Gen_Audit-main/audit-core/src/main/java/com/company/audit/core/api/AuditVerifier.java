package com.company.audit.core.api;

import com.company.audit.core.internal.DefaultAuditVerifier;
import com.company.audit.core.port.ChainRepository;

/**
 * Verifies the integrity of a partition's tamper-evident hash chain.
 *
 * <p>Obtain instances via {@link #create}; the implementation is intentionally hidden.
 */
public interface AuditVerifier {

    /**
     * Creates an {@code AuditVerifier} backed by the given repository.
     *
     * @param repository the storage port to read records from
     * @return a new {@code AuditVerifier}
     */
    static AuditVerifier create(ChainRepository repository) {
        return new DefaultAuditVerifier(repository);
    }

    /**
     * Verifies the hash chain of the given partition from its genesis hash through its most
     * recently appended record.
     *
     * @param partitionKey the key identifying the partition to verify
     * @param partitionContext the context of the partition, used to recompute its genesis hash
     * @return the outcome of verification
     */
    VerificationResult verify(String partitionKey, PartitionContext partitionContext);
}
