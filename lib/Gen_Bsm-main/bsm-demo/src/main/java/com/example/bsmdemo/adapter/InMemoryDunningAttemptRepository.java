package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.enums.DunningAttemptStatus;
import com.company.bsmsvc.domain.model.DunningAttempt;
import com.company.bsmsvc.domain.port.DunningAttemptRepositoryPort;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryDunningAttemptRepository implements DunningAttemptRepositoryPort {

    private final ConcurrentHashMap<UUID, DunningAttempt> store = new ConcurrentHashMap<>();

    @Override
    public DunningAttempt save(DunningAttempt attempt) {
        if (attempt.getId() == null) {
            attempt = attempt.toBuilder().id(UUID.randomUUID()).build();
        }
        store.put(attempt.getId(), attempt);
        return attempt;
    }

    @Override
    public Optional<DunningAttempt> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<DunningAttempt> findBySubscriptionId(UUID subscriptionId) {
        return store.values().stream()
            .filter(a -> subscriptionId.equals(a.getSubscriptionId()))
            .toList();
    }

    @Override
    public Optional<DunningAttempt> findLatestBySubscriptionId(UUID subscriptionId) {
        return store.values().stream()
            .filter(a -> subscriptionId.equals(a.getSubscriptionId()))
            .max(Comparator.comparing(DunningAttempt::getAttemptedAt, Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    @Override
    public List<DunningAttempt> findDuePending(Instant now) {
        return store.values().stream()
            .filter(a -> a.getStatus() == DunningAttemptStatus.PENDING)
            .filter(a -> a.getNextRetryAt() != null && !a.getNextRetryAt().isAfter(now))
            .toList();
    }
}
