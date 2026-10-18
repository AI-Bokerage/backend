package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.port.TenantBillingProfileRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryTenantBillingProfileRepository implements TenantBillingProfileRepositoryPort {

    private final ConcurrentHashMap<UUID, TenantBillingProfile> store = new ConcurrentHashMap<>();

    @Override
    public TenantBillingProfile save(TenantBillingProfile profile) {
        if (profile.getId() == null) {
            profile = profile.toBuilder().id(UUID.randomUUID()).build();
        }
        store.put(profile.getId(), profile);
        return profile;
    }

    @Override
    public Optional<TenantBillingProfile> findByTenantId(UUID tenantId) {
        return store.values().stream()
            .filter(p -> tenantId.equals(p.getTenantId()))
            .findFirst();
    }
}
