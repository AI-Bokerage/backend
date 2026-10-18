package com.company.audit.spring.sharding;

/**
 * Answers "does this instance own this partition" — the generic mechanism a multi-instance
 * deployment uses to decide whether to process a given partition's messages locally or leave
 * them for whichever other instance actually owns them.
 *
 * <p>This is deliberately just an ownership question, not a routing or discovery mechanism: the
 * actual instance topology (how many instances exist, which index this one is) is a deployment
 * concern this library has no way to know generically, and is supplied entirely via
 * configuration (see {@code AuditProperties.Rabbit.Shard}) — this interface's only job is to
 * turn that configuration plus a partition key into a yes/no answer.
 *
 * <p><b>This library cannot validate that every real instance is consistently configured</b> (the
 * same total instance count, a distinct index each). That is the deploying platform's
 * responsibility; a single instance has no way to inspect what any other instance believes its
 * own configuration to be, so this interface's implementations do not attempt to guess or detect
 * misconfiguration — they simply compute an answer from what they were given.
 */
public interface PartitionShardResolver {

    /**
     * Returns whether this instance owns the given partition.
     *
     * @param partitionKey the partition key to check
     * @return {@code true} if this instance should process events for this partition
     */
    boolean ownsPartition(String partitionKey);
}
