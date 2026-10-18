package com.company.audit.spring.sharding;

/**
 * A {@link PartitionShardResolver} using {@link String#hashCode()} modulo the configured
 * instance count to assign each partition key to exactly one instance index.
 *
 * <p><b>{@code String.hashCode()} is a pragmatic v1 choice, not a fundamental design
 * guarantee.</b> It is specified and stable across JVM restarts (unlike the default
 * {@code Object.hashCode()}, which is identity-based and would make this mapping meaningless
 * across process boundaries), so it is safe to rely on for a deterministic, restart-stable
 * assignment. But its distribution across arbitrary strings is genuinely mediocre compared to a
 * purpose-built hash function (Murmur3, xxHash, etc.) — short or similarly-structured partition
 * keys can skew toward particular buckets more than a proper hash would. A real hash library was
 * deliberately not introduced for this: this project's "dependency-free where possible"
 * discipline doesn't justify a new dependency for better distribution when no evidence yet shows
 * production partition keys actually skew under this one. If production data ever does show
 * skewed distribution across instances, this is the place a future major version should swap in
 * a better hash — recorded here so that decision doesn't need to be rediscovered from scratch.
 */
public class ConsistentHashPartitionShardResolver implements PartitionShardResolver {

    private final int instanceIndex;
    private final int totalInstances;

    /**
     * Creates a new resolver.
     *
     * @param instanceIndex this instance's own index, in {@code [0, totalInstances)}
     * @param totalInstances the total number of instances sharing partition ownership
     */
    public ConsistentHashPartitionShardResolver(int instanceIndex, int totalInstances) {
        if (totalInstances < 1) {
            throw new IllegalArgumentException("totalInstances must be at least 1, was " + totalInstances);
        }
        if (instanceIndex < 0 || instanceIndex >= totalInstances) {
            throw new IllegalArgumentException(
                    "instanceIndex must be in [0, " + totalInstances + "), was " + instanceIndex);
        }
        this.instanceIndex = instanceIndex;
        this.totalInstances = totalInstances;
    }

    @Override
    public boolean ownsPartition(String partitionKey) {
        return ownerIndex(partitionKey) == instanceIndex;
    }

    /**
     * Returns the instance index a given partition key is assigned to under this resolver's
     * configured instance count — exposed so a caller (for example, diagnostic logging on a
     * rejection) can report which instance a mismatched partition actually belongs to, without
     * duplicating the hash formula itself.
     *
     * @param partitionKey the partition key to compute an owner for
     * @return the owning instance's index
     */
    public int ownerIndex(String partitionKey) {
        return Math.floorMod(partitionKey.hashCode(), totalInstances);
    }
}
