package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.model.SubscriptionAddOn;
import com.company.bsmsvc.domain.port.SubscriptionAddOnRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemorySubscriptionAddOnRepository implements SubscriptionAddOnRepositoryPort {

    private final ConcurrentHashMap<UUID, SubscriptionAddOn> store = new ConcurrentHashMap<>();

    @Override
    public SubscriptionAddOn save(SubscriptionAddOn addOn) {
        if (addOn.getId() == null) {
            addOn = addOn.toBuilder().id(UUID.randomUUID()).build();
        }
        store.put(addOn.getId(), addOn);
        return addOn;
    }

    @Override
    public List<SubscriptionAddOn> findBySubscriptionId(UUID subscriptionId) {
        return store.values().stream()
            .filter(a -> subscriptionId.equals(a.getSubscriptionId()))
            .toList();
    }

    @Override
    public List<SubscriptionAddOn> findActiveBySubscriptionId(UUID subscriptionId) {
        return store.values().stream()
            .filter(a -> subscriptionId.equals(a.getSubscriptionId()))
            .filter(SubscriptionAddOn::isActive)
            .toList();
    }

    @Override
    public Optional<SubscriptionAddOn> findActiveBySubscriptionIdAndPpmAddOnId(UUID subscriptionId, UUID ppmAddOnId) {
        return store.values().stream()
            .filter(a -> subscriptionId.equals(a.getSubscriptionId()))
            .filter(a -> ppmAddOnId.equals(a.getPpmAddOnId()))
            .filter(SubscriptionAddOn::isActive)
            .findFirst();
    }

    @Override
    public boolean existsActive(UUID subscriptionId, UUID ppmAddOnId) {
        return findActiveBySubscriptionIdAndPpmAddOnId(subscriptionId, ppmAddOnId).isPresent();
    }
}
