package com.company.bsmsvc.infrastructure.persistence.adapter;

import com.company.bsmsvc.domain.model.SubscriptionHistory;
import com.company.bsmsvc.domain.model.SubscriptionHistoryFilter;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionEntity;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionHistoryEntity;
import com.company.bsmsvc.infrastructure.persistence.mapper.SubscriptionHistoryEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.SubscriptionHistoryJpaRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SubscriptionHistoryRepositoryAdapter implements SubscriptionHistoryRepositoryPort {

    private final SubscriptionHistoryJpaRepository subscriptionHistoryJpaRepository;
    private final SubscriptionHistoryEntityMapper subscriptionHistoryEntityMapper;
    private final EntityManager entityManager;
    private static final Instant MIN_INSTANT = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant MAX_INSTANT = Instant.parse("9999-12-31T23:59:59Z");

    @Override
    public SubscriptionHistory save(SubscriptionHistory history) {
        SubscriptionHistoryEntity entity = subscriptionHistoryEntityMapper.toEntity(history);
        entity.setSubscription(entityManager.getReference(SubscriptionEntity.class, history.getSubscriptionId()));
        return subscriptionHistoryEntityMapper.toDomain(
            subscriptionHistoryJpaRepository.save(entity)
        );
    }

    @Override
    public PageResult<SubscriptionHistory> findHistory(
        SubscriptionHistoryFilter filter,
        int page,
        int size,
        String sortBy,
        String sortDirection
    ) {
        Sort sort = "desc".equalsIgnoreCase(sortDirection)
            ? Sort.by(sortBy).descending()
            : Sort.by(sortBy).ascending();
        boolean applyDateFrom = filter.dateFrom() != null;
        boolean applyDateTo = filter.dateTo() != null;
        Page<SubscriptionHistoryEntity> result = subscriptionHistoryJpaRepository.findHistory(
            filter.tenantId(),
            filter.subscriptionId(),
            filter.action() == null ? null : filter.action().name(),
            applyDateFrom,
            applyDateFrom ? filter.dateFrom() : MIN_INSTANT,
            applyDateTo,
            applyDateTo ? filter.dateTo() : MAX_INSTANT,
            PageRequest.of(page, size, sort)
        );
        java.util.List<SubscriptionHistory> content = result.getContent().stream().map(subscriptionHistoryEntityMapper::toDomain).toList();
        return new PageResult<>(
            content,
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.hasNext()
        );
    }
}
