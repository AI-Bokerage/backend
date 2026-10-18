package com.company.audit.spring.persistence.mongo.repository;

import com.company.audit.spring.persistence.mongo.document.AuditEventDocument;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data repository for {@link AuditEventDocument}.
 */
public interface SpringDataAuditEventMongoRepository extends MongoRepository<AuditEventDocument, String> {

    /**
     * Returns all documents for the given partition.
     *
     * @param partitionKey the partition key
     * @return the partition's documents
     */
    List<AuditEventDocument> findByPartitionKey(String partitionKey);
}
