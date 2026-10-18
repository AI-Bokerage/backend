package com.company.bsmsvc.infrastructure.persistence.adapter;

import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.SubscriptionEvent;
import com.company.bsmsvc.domain.model.SubscriptionEventFilter;
import com.company.bsmsvc.domain.port.SubscriptionEventRepositoryPort;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionEntity;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionEventEntity;
import com.company.bsmsvc.infrastructure.persistence.mapper.SubscriptionEventEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.SubscriptionEventJpaRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SubscriptionEventRepositoryAdapter implements SubscriptionEventRepositoryPort {

    private final SubscriptionEventJpaRepository subscriptionEventJpaRepository;
    private final SubscriptionEventEntityMapper subscriptionEventEntityMapper;
    private final EntityManager entityManager;
    private static final Instant MIN_INSTANT = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant MAX_INSTANT = Instant.parse("9999-12-31T23:59:59Z");

    @Override
    public SubscriptionEvent save(SubscriptionEvent event) {
        SubscriptionEventEntity entity = subscriptionEventEntityMapper.toEntity(event);
        entity.setSubscription(entityManager.getReference(SubscriptionEntity.class, event.getSubscriptionId()));
        return subscriptionEventEntityMapper.toDomain(subscriptionEventJpaRepository.save(entity));
    }

    @Override
    public PageResult<SubscriptionEvent> findEvents(
        SubscriptionEventFilter filter,
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
        Page<SubscriptionEventEntity> result = subscriptionEventJpaRepository.findEvents(
            filter.tenantId(),
            filter.subscriptionId(),
            filter.eventType() == null ? null : filter.eventType().name(),
            applyDateFrom,
            applyDateFrom ? filter.dateFrom() : MIN_INSTANT,
            applyDateTo,
            applyDateTo ? filter.dateTo() : MAX_INSTANT,
            PageRequest.of(page, size, sort)
        );
        return new PageResult<>(
            result.getContent().stream().map(subscriptionEventEntityMapper::toDomain).toList(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.hasNext()
        );
    }
}
