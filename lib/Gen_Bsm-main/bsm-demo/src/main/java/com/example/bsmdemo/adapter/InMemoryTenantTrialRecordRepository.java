package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.port.TenantTrialRecordRepositoryPort;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryTenantTrialRecordRepository implements TenantTrialRecordRepositoryPort {

    private record TrialRecord(UUID subscriptionId, Instant consumedAt) {}

    private final ConcurrentHashMap<UUID, TrialRecord> store = new ConcurrentHashMap<>();

    @Override
    public boolean existsByTenantId(UUID tenantId) {
        return store.containsKey(tenantId);
    }

    @Override
    public void markTrialConsumed(UUID tenantId, UUID subscriptionId, Instant consumedAt) {
        // ponytail: no unique-constraint race simulation, single JVM in-memory map is enough for a demo
        store.putIfAbsent(tenantId, new TrialRecord(subscriptionId, consumedAt));
    }
}
