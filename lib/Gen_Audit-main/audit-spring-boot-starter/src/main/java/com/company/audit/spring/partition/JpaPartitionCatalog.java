package com.company.audit.spring.partition;

import com.company.audit.core.api.PartitionContext;
import com.company.audit.spring.persistence.jpa.repository.SpringDataChainPartitionRepository;
import java.util.List;

/**
 * A {@link PartitionCatalog} backed by the {@code chain_partition} table.
 *
 * <p>Not component-scanned: registered explicitly as a bean by
 * {@link com.company.audit.spring.autoconfigure.AuditVerificationAutoConfiguration}, matching how
 * a starter's internal wiring is declared elsewhere in this module.
 */
public class JpaPartitionCatalog implements PartitionCatalog {

    private final SpringDataChainPartitionRepository repository;

    /**
     * Creates a new catalog adapter.
     *
     * @param repository the underlying Spring Data repository
     */
    public JpaPartitionCatalog(SpringDataChainPartitionRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<PartitionContext> listAll() {
        return repository.findAll().stream()
                .map(entity -> new PartitionContext(entity.getPartitionKey(), entity.getCreatedAt()))
                .toList();
    }
}
