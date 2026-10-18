package com.company.bsmsvc.infrastructure.persistence.adapter;

import com.company.bsmsvc.domain.enums.SubscriptionScheduleActionType;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.SubscriptionSchedule;
import com.company.bsmsvc.domain.model.SubscriptionScheduleFilter;
import com.company.bsmsvc.domain.port.SubscriptionScheduleRepositoryPort;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionEntity;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionScheduleEntity;
import com.company.bsmsvc.infrastructure.persistence.mapper.SubscriptionScheduleEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.SubscriptionScheduleJpaRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SubscriptionScheduleRepositoryAdapter implements SubscriptionScheduleRepositoryPort {

    private final SubscriptionScheduleJpaRepository subscriptionScheduleJpaRepository;
    private final SubscriptionScheduleEntityMapper subscriptionScheduleEntityMapper;
    private final EntityManager entityManager;

    @Override
    public SubscriptionSchedule save(SubscriptionSchedule schedule) {
        SubscriptionScheduleEntity entity = subscriptionScheduleEntityMapper.toEntity(schedule);
        entity.setSubscription(entityManager.getReference(SubscriptionEntity.class, schedule.getSubscriptionId()));
        return subscriptionScheduleEntityMapper.toDomain(
            subscriptionScheduleJpaRepository.save(entity)
        );
    }

    @Override
    public Optional<SubscriptionSchedule> findPendingBySubscriptionIdAndActionType(UUID subscriptionId, SubscriptionScheduleActionType actionType) {
        return subscriptionScheduleJpaRepository.findPendingBySubscriptionIdAndActionType(subscriptionId, actionType.name())
            .map(subscriptionScheduleEntityMapper::toDomain);
    }

    @Override
    public List<SubscriptionSchedule> findDueSchedules(Instant asOf) {
        return subscriptionScheduleJpaRepository.findDueSchedules(asOf)
            .stream()
            .map(subscriptionScheduleEntityMapper::toDomain)
            .toList();
    }

    @Override
    public PageResult<SubscriptionSchedule> findSchedules(
        SubscriptionScheduleFilter filter,
        int page,
        int size,
        String sortBy,
        String sortDirection
    ) {
        Sort sort = "desc".equalsIgnoreCase(sortDirection)
            ? Sort.by(sortBy).descending()
            : Sort.by(sortBy).ascending();
        Page<SubscriptionScheduleEntity> result = subscriptionScheduleJpaRepository.findSchedules(
            filter.tenantId(),
            filter.subscriptionId(),
            filter.status() == null ? null : filter.status().name(),
            filter.actionType() == null ? null : filter.actionType().name(),
            PageRequest.of(page, size, sort)
        );
        return new PageResult<>(
            result.getContent().stream().map(subscriptionScheduleEntityMapper::toDomain).toList(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.hasNext()
        );
    }
}
