package com.company.audit.spring.partition;

import com.company.audit.core.api.PartitionContext;

/**
 * Resolves a stable {@link PartitionContext} for a given partition key.
 *
 * <p>This is starter-local surface, not part of {@code audit-core}'s frozen API. It exists
 * because {@code PartitionContext.createdAt()} must be the exact same value on every call for a
 * given partition key, or the genesis hash — and the whole chain — silently diverges between
 * calls. Relying on every caller to consistently pass the identical timestamp forever is fragile;
 * this registry makes that consistency automatic instead. The caller has no business deciding or
 * knowing anything about partition-creation bookkeeping — that is entirely this component's job,
 * not something exposed on its API surface.
 *
 * <p><b>Concurrency contract</b> (a hard guarantee, not just typical behavior):
 * <ol>
 *   <li>Concurrent first-time calls for the same new {@code partitionKey} resolve to exactly one
 *       persisted creation timestamp — no caller can ever observe two different genesis values
 *       for the same partition, even under a race between callers.</li>
 *   <li>Repeated calls for an already-known {@code partitionKey} always return the same
 *       {@link PartitionContext} it returned the first time, for the entire lifetime of that
 *       partition.</li>
 * </ol>
 */
public interface PartitionRegistry {

    /**
     * Returns the stable, persisted {@link PartitionContext} for {@code partitionKey}, creating
     * it (with an implementation-decided creation instant) on the first call for that key.
     *
     * @param partitionKey the partition key to resolve
     * @return the stable partition context for {@code partitionKey}
     */
    PartitionContext resolve(String partitionKey);
}
