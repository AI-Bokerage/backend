package com.company.audit.spring.persistence.jpa.repository;

import com.company.audit.spring.persistence.jpa.entity.ChainPartitionEntity;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link ChainPartitionEntity}.
 */
public interface SpringDataChainPartitionRepository extends JpaRepository<ChainPartitionEntity, String> {

    /**
     * Inserts a new partition row if one doesn't already exist for {@code partitionKey}, doing
     * nothing otherwise.
     *
     * <p>Using {@code ON CONFLICT DO NOTHING} rather than a check-then-insert closes the race
     * window that exists under concurrent first-writers for the same partition.
     *
     * @param partitionKey the partition key
     * @param createdAt the genesis instant to record if this is the first insert
     */
    @Modifying
    @Query(
            value = "INSERT INTO chain_partition (partition_key, created_at) "
                    + "VALUES (:partitionKey, :createdAt) ON CONFLICT (partition_key) DO NOTHING",
            nativeQuery = true)
    void insertIfAbsent(@Param("partitionKey") String partitionKey, @Param("createdAt") Instant createdAt);
}
