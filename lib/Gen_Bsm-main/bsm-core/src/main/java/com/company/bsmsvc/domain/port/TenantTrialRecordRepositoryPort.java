package com.company.bsmsvc.domain.port;

import java.time.Instant;
import java.util.UUID;

public interface TenantTrialRecordRepositoryPort {

    /**
     * Returns true if the tenant has already consumed their lifetime trial.
     * Called inside the same transaction as subscription creation so the check
     * is isolated at the READ COMMITTED level.
     */
    boolean existsByTenantId(UUID tenantId);

    /**
     * Marks the trial as consumed.  Called immediately after a TRIALING subscription
     * row is saved, within the same transaction.  The PRIMARY KEY on tenant_id in the
     * underlying table ensures concurrent inserts from two nodes result in exactly one
     * winner — the loser's transaction rolls back on the constraint violation.
     */
    void markTrialConsumed(UUID tenantId, UUID subscriptionId, Instant consumedAt);
}
