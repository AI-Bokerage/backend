package com.company.bsmsvc.infrastructure.persistence.adapter;

import com.company.bsmsvc.domain.model.SubscriptionAddOn;
import com.company.bsmsvc.domain.port.SubscriptionAddOnRepositoryPort;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionAddOnEntity;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionEntity;
import com.company.bsmsvc.infrastructure.persistence.mapper.SubscriptionAddOnEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.SubscriptionAddOnJpaRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SubscriptionAddOnRepositoryAdapter implements SubscriptionAddOnRepositoryPort {

    private final SubscriptionAddOnJpaRepository jpaRepository;
    private final SubscriptionAddOnEntityMapper  mapper;
    private final EntityManager                  entityManager;

    @Override
    public SubscriptionAddOn save(SubscriptionAddOn addOn) {
        SubscriptionAddOnEntity entity = mapper.toEntity(addOn);
        entity.setSubscription(
            entityManager.getReference(SubscriptionEntity.class, addOn.getSubscriptionId()));
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public List<SubscriptionAddOn> findBySubscriptionId(UUID subscriptionId) {
        return jpaRepository.findBySubscription_Id(subscriptionId)
            .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<SubscriptionAddOn> findActiveBySubscriptionId(UUID subscriptionId) {
        return jpaRepository.findBySubscription_IdAndActiveTrue(subscriptionId)
            .stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<SubscriptionAddOn> findActiveBySubscriptionIdAndPpmAddOnId(
            UUID subscriptionId, UUID ppmAddOnId) {
        return jpaRepository
            .findBySubscription_IdAndPpmAddOnIdAndActiveTrue(subscriptionId, ppmAddOnId)
            .map(mapper::toDomain);
    }

    @Override
    public boolean existsActive(UUID subscriptionId, UUID ppmAddOnId) {
        return jpaRepository.existsBySubscription_IdAndPpmAddOnIdAndActiveTrue(subscriptionId, ppmAddOnId);
    }
}
