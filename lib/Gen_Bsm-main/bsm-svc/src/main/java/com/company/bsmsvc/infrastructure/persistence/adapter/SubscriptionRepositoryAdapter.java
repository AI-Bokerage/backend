package com.company.bsmsvc.infrastructure.persistence.adapter;

import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.exception.ConcurrentUpdateException;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.infrastructure.persistence.mapper.SubscriptionEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.SubscriptionJpaRepository;
import jakarta.persistence.OptimisticLockException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SubscriptionRepositoryAdapter implements SubscriptionRepositoryPort {

    private static final List<SubscriptionStatus> CURRENT_STATUSES = List.of(
        SubscriptionStatus.TRIALING,
        SubscriptionStatus.ACTIVE,
        SubscriptionStatus.PAUSED,
        SubscriptionStatus.PAST_DUE,
        SubscriptionStatus.SUSPENDED_PENDING_PURGE
    );

    private final SubscriptionJpaRepository subscriptionJpaRepository;
    private final SubscriptionEntityMapper subscriptionEntityMapper;

    @Override
    public Subscription save(Subscription subscription) {
        try {
            return subscriptionEntityMapper.toDomain(subscriptionJpaRepository.save(subscriptionEntityMapper.toEntity(subscription)));
        } catch (OptimisticLockingFailureException | OptimisticLockException e) {
            throw new ConcurrentUpdateException("Subscription " + subscription.getId() + " was updated concurrently", e);
        }
    }

    @Override
    public Optional<Subscription> findCurrentByTenantId(UUID tenantId) {
        return subscriptionJpaRepository.findTopByTenantIdAndStatusInOrderByCreatedAtDesc(tenantId, CURRENT_STATUSES)
            .map(subscriptionEntityMapper::toDomain);
    }

    @Override
    public Optional<Subscription> findById(UUID id) {
        return subscriptionJpaRepository.findById(id).map(subscriptionEntityMapper::toDomain);
    }

    @Override
    public Optional<Subscription> findCurrentBySubscriptionId(UUID subscriptionId, java.util.Collection<SubscriptionStatus> statuses) {
        return subscriptionJpaRepository.findTopByIdAndStatusInOrderByCreatedAtDesc(subscriptionId, statuses)
            .map(subscriptionEntityMapper::toDomain);
    }

    @Override
    public List<Subscription> findDueForRenewal(Instant asOf) {
        return subscriptionJpaRepository.findDueForRenewal(asOf)
            .stream()
            .map(subscriptionEntityMapper::toDomain)
            .toList();
    }

    @Override
    public Optional<Subscription> findByExternalSubscriptionId(String externalSubscriptionId) {
        return subscriptionJpaRepository.findByExternalSubscriptionId(externalSubscriptionId)
            .map(subscriptionEntityMapper::toDomain);
    }

    @Override
    public List<Subscription> findExpiredTrials(Instant asOf) {
        return subscriptionJpaRepository.findExpiredTrials(asOf)
            .stream()
            .map(subscriptionEntityMapper::toDomain)
            .toList();
    }

    @Override
    public List<Subscription> findPendingProviderSync() {
        return subscriptionJpaRepository.findByExternalSubscriptionIdIsNullAndStatusIn(CURRENT_STATUSES)
            .stream()
            .map(subscriptionEntityMapper::toDomain)
            .toList();
    }
}
