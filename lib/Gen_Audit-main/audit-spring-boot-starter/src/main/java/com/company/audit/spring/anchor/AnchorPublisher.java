package com.company.audit.spring.anchor;

import com.company.audit.core.api.ChainedRecord;
import com.company.audit.core.port.ChainRepository;
import com.company.audit.core.port.ObjectLockPort;
import java.time.Clock;

/**
 * Publishes a WORM anchor of a single partition's current chain tip.
 *
 * <p>A regular Spring-managed orchestration bean — like {@code AuditRecorder} in {@code
 * ingestion}, not like {@code audit-core}'s hidden-implementation, static-factory pattern — since
 * this class is not part of a frozen, versioned public library surface the way {@code
 * audit-core}'s types are. {@code @ConditionalOnMissingBean} is the swap-out mechanism here, the
 * same as everywhere else in this starter.
 *
 * <p>Needs neither {@code PartitionRegistry} nor {@code PartitionCatalog}: publishing an anchor
 * for one partition only needs that partition's own chain tip, nothing about partition-creation
 * bookkeeping or enumeration.
 */
public class AnchorPublisher {

    private final ChainRepository chainRepository;
    private final ObjectLockPort objectLockPort;
    private final Clock clock;

    /**
     * Creates a new publisher.
     *
     * @param chainRepository reads the partition's current chain tip
     * @param objectLockPort publishes the anchor to immutable object storage
     * @param clock the clock used to timestamp the published anchor
     */
    public AnchorPublisher(ChainRepository chainRepository, ObjectLockPort objectLockPort, Clock clock) {
        this.chainRepository = chainRepository;
        this.objectLockPort = objectLockPort;
        this.clock = clock;
    }

    /**
     * Publishes an anchor of {@code partitionKey}'s current chain tip.
     *
     * @param partitionKey the partition to anchor
     * @return the opaque storage reference {@link ObjectLockPort} returned for this anchor
     * @throws IllegalStateException if the partition has no chain at all yet
     */
    public String publish(String partitionKey) {
        ChainedRecord tip = chainRepository
                .findTip(partitionKey)
                .orElseThrow(() -> new IllegalStateException("No chain exists for partition " + partitionKey));
        return objectLockPort.publishAnchor(partitionKey, tip.eventHash(), clock.instant());
    }
}
