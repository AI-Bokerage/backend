package com.company.bsmsvc.infrastructure.persistence.repository;

import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionAddOnEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionAddOnJpaRepository extends JpaRepository<SubscriptionAddOnEntity, UUID> {

    List<SubscriptionAddOnEntity> findBySubscription_Id(UUID subscriptionId);

    List<SubscriptionAddOnEntity> findBySubscription_IdAndActiveTrue(UUID subscriptionId);

    Optional<SubscriptionAddOnEntity> findBySubscription_IdAndPpmAddOnIdAndActiveTrue(
            UUID subscriptionId, UUID ppmAddOnId);

    boolean existsBySubscription_IdAndPpmAddOnIdAndActiveTrue(UUID subscriptionId, UUID ppmAddOnId);
}
