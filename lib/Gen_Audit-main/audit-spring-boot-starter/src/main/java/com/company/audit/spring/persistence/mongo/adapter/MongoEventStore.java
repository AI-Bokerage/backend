package com.company.audit.spring.persistence.mongo.adapter;

import com.company.audit.core.api.AuditEvent;
import com.company.audit.core.api.ChainedRecord;
import com.company.audit.spring.persistence.mongo.mapper.AuditEventMongoMapper;
import com.company.audit.spring.persistence.mongo.repository.SpringDataAuditEventMongoRepository;
import com.company.audit.spring.port.EventStore;
import java.util.List;

/**
 * An {@link EventStore} backed by a Mongo collection via Spring Data MongoDB.
 *
 * <p>Not component-scanned: registered explicitly as a bean by
 * {@link com.company.audit.spring.autoconfigure.AuditMongoAutoConfiguration}, matching how a
 * starter's internal wiring is declared elsewhere in this module.
 */
public class MongoEventStore implements EventStore {

    private final SpringDataAuditEventMongoRepository repository;
    private final AuditEventMongoMapper mapper;

    /**
     * Creates a new store adapter.
     *
     * @param repository the underlying Spring Data repository
     * @param mapper the mapper between domain and document types
     */
    public MongoEventStore(SpringDataAuditEventMongoRepository repository, AuditEventMongoMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public void save(AuditEvent event, ChainedRecord record) {
        repository.save(mapper.toDocument(event, record));
    }

    @Override
    public List<AuditEvent> findByPartitionKey(String partitionKey) {
        return repository.findByPartitionKey(partitionKey).stream().map(mapper::toDomain).toList();
    }
}
