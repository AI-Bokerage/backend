package com.company.ppmdemo.adapter;

import com.company.ppmsvc.module.model.Module;
import com.company.ppmsvc.module.model.ModuleCode;
import com.company.ppmsvc.module.port.ModuleRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** In-memory {@link ModuleRepositoryPort} implementation — see InMemoryPlanRepositoryAdapter. */
@Component
public class InMemoryModuleRepositoryAdapter implements ModuleRepositoryPort {

    private final Map<UUID, Module> store = new ConcurrentHashMap<>();

    @Override
    public Module save(Module module) {
        Module withVersion = Module.builder()
            .id(module.getId()).version(module.getVersion() == null ? 0L : module.getVersion() + 1)
            .code(module.getCode()).name(module.getName()).description(module.getDescription())
            .active(module.isActive())
            .createdAt(module.getCreatedAt() == null ? Instant.now() : module.getCreatedAt())
            .updatedAt(Instant.now())
            .createdBy(module.getCreatedBy()).updatedBy(module.getUpdatedBy())
            .build();
        store.put(withVersion.getId(), withVersion);
        return withVersion;
    }

    @Override
    public Optional<Module> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Module> findByCode(ModuleCode code) {
        return store.values().stream().filter(m -> m.getCode() == code).findFirst();
    }

    @Override
    public boolean existsByCode(ModuleCode code) {
        return store.values().stream().anyMatch(m -> m.getCode() == code);
    }

    @Override
    public List<Module> findAll() {
        return store.values().stream()
            .sorted((a, b) -> a.getCode().compareTo(b.getCode()))
            .toList();
    }

    @Override
    public void softDelete(UUID id, UUID actorId) {
        store.computeIfPresent(id, (k, existing) -> Module.builder()
            .id(existing.getId()).version(existing.getVersion() + 1)
            .code(existing.getCode()).name(existing.getName()).description(existing.getDescription())
            .active(false)
            .createdAt(existing.getCreatedAt()).updatedAt(Instant.now())
            .createdBy(existing.getCreatedBy()).updatedBy(actorId)
            .build());
    }
}
