package com.company.audit.spring.persistence.jpa.adapter;

import com.company.audit.core.api.PartitionContext;
import com.company.audit.spring.partition.PartitionRegistry;
import com.company.audit.spring.persistence.jpa.repository.SpringDataChainPartitionRepository;
import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;

/**
 * A {@link PartitionRegistry} backed by the {@code chain_partition} table.
 *
 * <p>Not component-scanned: registered explicitly as a bean by
 * {@link com.company.audit.spring.autoconfigure.AuditJpaAutoConfiguration}.
 */
public class JpaPartitionRegistry implements PartitionRegistry {

    private final SpringDataChainPartitionRepository repository;
    private final Clock clock;

    /**
     * Creates a new registry adapter.
     *
     * @param repository the underlying Spring Data repository
     * @param clock the clock used to stamp a partition's creation instant on first resolution
     */
    public JpaPartitionRegistry(SpringDataChainPartitionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PartitionContext resolve(String partitionKey) {
        repository.insertIfAbsent(partitionKey, clock.instant());
        var row = repository.findById(partitionKey).orElseThrow();
        return new PartitionContext(partitionKey, row.getCreatedAt());
    }
}
