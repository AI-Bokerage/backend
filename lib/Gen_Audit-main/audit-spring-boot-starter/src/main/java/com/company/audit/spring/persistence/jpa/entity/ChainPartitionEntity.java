package com.company.audit.spring.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity mirroring the {@code chain_partition} table 1:1.
 *
 * <p>A row is written once per {@code partitionKey} and never updated afterward — changing
 * {@code createdAt} after the fact would silently invalidate every hash computed against the
 * old genesis.
 */
@Entity
@Table(name = "chain_partition")
public class ChainPartitionEntity {

    @Id
    @Column(name = "partition_key")
    private String partitionKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Required by JPA.
     */
    protected ChainPartitionEntity() {
    }

    /**
     * Creates a new partition row.
     *
     * @param partitionKey the partition key
     * @param createdAt the partition's stable genesis instant
     */
    public ChainPartitionEntity(String partitionKey, Instant createdAt) {
        this.partitionKey = partitionKey;
        this.createdAt = createdAt;
    }

    /**
     * Returns the partition key.
     *
     * @return the partition key
     */
    public String getPartitionKey() {
        return partitionKey;
    }

    /**
     * Returns the partition's stable genesis instant.
     *
     * @return the created-at instant
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
