package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemorySubscriptionRepository implements SubscriptionRepositoryPort {

    private final ConcurrentHashMap<UUID, Subscription> store = new ConcurrentHashMap<>();

    @Override
    public Subscription save(Subscription subscription) {
        if (subscription.getId() == null) {
            subscription = subscription.toBuilder().id(UUID.randomUUID()).build();
        }
        store.put(subscription.getId(), subscription);
        return subscription;
    }

    @Override
    public Optional<Subscription> findCurrentByTenantId(UUID tenantId) {
        return store.values().stream()
            .filter(s -> tenantId.equals(s.getTenantId()))
            .filter(s -> s.getStatus() != SubscriptionStatus.CANCELLED)
            .findFirst();
    }

    @Override
    public Optional<Subscription> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Subscription> findCurrentBySubscriptionId(UUID subscriptionId, Collection<SubscriptionStatus> statuses) {
        return Optional.ofNullable(store.get(subscriptionId))
            .filter(s -> statuses == null || statuses.isEmpty() || statuses.contains(s.getStatus()));
    }

    @Override
    public List<Subscription> findDueForRenewal(Instant asOf) {
        return store.values().stream()
            .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
            .filter(s -> s.getCurrentPeriodEnd() != null && !s.getCurrentPeriodEnd().isAfter(asOf))
            .toList();
    }

    @Override
    public Optional<Subscription> findByExternalSubscriptionId(String externalSubscriptionId) {
        return store.values().stream()
            .filter(s -> externalSubscriptionId.equals(s.getExternalSubscriptionId()))
            .findFirst();
    }

    @Override
    public List<Subscription> findExpiredTrials(Instant asOf) {
        return store.values().stream()
            .filter(s -> s.getStatus() == SubscriptionStatus.TRIALING)
            .filter(s -> s.getTrialEndsAt() != null && !s.getTrialEndsAt().isAfter(asOf))
            .toList();
    }

    @Override
    public List<Subscription> findPendingProviderSync() {
        return store.values().stream()
            .filter(s -> s.getStatus() != SubscriptionStatus.CANCELLED)
            .filter(s -> s.getExternalSubscriptionId() == null || s.getExternalSubscriptionId().isBlank())
            .toList();
    }
}
