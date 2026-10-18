package com.company.audit.spring.persistence.jpa.repository;

import com.company.audit.spring.persistence.jpa.entity.AuditImmutableEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link AuditImmutableEntity}.
 */
public interface SpringDataAuditImmutableRepository extends JpaRepository<AuditImmutableEntity, UUID> {

    /**
     * Returns the record with the highest sequence number for the given partition.
     *
     * @param partitionKey the partition key
     * @return the tip record, or empty if the partition has no records
     */
    Optional<AuditImmutableEntity> findTopByPartitionKeyOrderBySeqDesc(String partitionKey);

    /**
     * Returns all records for the given partition, ordered by ascending sequence number.
     *
     * @param partitionKey the partition key
     * @return the partition's records in ascending sequence order
     */
    List<AuditImmutableEntity> findByPartitionKeyOrderBySeqAsc(String partitionKey);
}
