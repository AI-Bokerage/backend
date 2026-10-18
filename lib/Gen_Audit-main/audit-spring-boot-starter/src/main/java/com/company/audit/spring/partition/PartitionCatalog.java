package com.company.audit.spring.partition;

import com.company.audit.core.api.PartitionContext;
import java.util.List;

/**
 * Enumerates every known partition.
 *
 * <p>This is a deliberately separate interface from {@link PartitionRegistry}, not an added
 * method on it: {@link PartitionRegistry#resolve} is a partition-lifecycle operation (it may
 * create a partition on first call), while {@link #listAll} is a read-only catalog query. The
 * two have genuinely different callers — a verification job has no business invoking a
 * write-capable lifecycle operation just to enumerate what already exists — so they stay
 * separate even though a single JPA repository can back both.
 *
 * <p>Returns full {@link PartitionContext} objects, not bare partition key strings: the
 * underlying storage already has {@code createdAt} alongside {@code partitionKey} on the same
 * row, so returning it costs nothing extra, and it's exactly what a caller like
 * {@code ChainVerifierJob} needs to verify a partition without depending on
 * {@link PartitionRegistry} at all.
 */
public interface PartitionCatalog {

    /**
     * Returns every known partition.
     *
     * @return the full set of persisted partitions, as {@link PartitionContext} objects
     */
    List<PartitionContext> listAll();
}
