package com.company.ppmdemo.adapter;

import com.company.ppmsvc.planmodule.model.PlanModule;
import com.company.ppmsvc.planmodule.port.PlanModuleRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** In-memory {@link PlanModuleRepositoryPort} implementation — see InMemoryPlanRepositoryAdapter. */
@Component
public class InMemoryPlanModuleRepositoryAdapter implements PlanModuleRepositoryPort {

    private final Map<UUID, PlanModule> store = new ConcurrentHashMap<>();

    @Override
    public PlanModule save(PlanModule mapping) {
        PlanModule withVersion = PlanModule.builder()
            .id(mapping.getId()).version(0L)
            .planId(mapping.getPlanId()).moduleId(mapping.getModuleId())
            .createdAt(Instant.now()).createdBy(mapping.getCreatedBy())
            .build();
        store.put(withVersion.getId(), withVersion);
        return withVersion;
    }

    @Override
    public List<PlanModule> saveAll(List<PlanModule> mappings) {
        return mappings.stream().map(this::save).toList();
    }

    @Override
    public List<PlanModule> findByPlanId(UUID planId) {
        return store.values().stream().filter(m -> m.getPlanId().equals(planId)).toList();
    }

    @Override
    public List<PlanModule> findByModuleId(UUID moduleId) {
        return store.values().stream().filter(m -> m.getModuleId().equals(moduleId)).toList();
    }

    @Override
    public boolean exists(UUID planId, UUID moduleId) {
        return store.values().stream()
            .anyMatch(m -> m.getPlanId().equals(planId) && m.getModuleId().equals(moduleId));
    }

    @Override
    public void delete(UUID planId, UUID moduleId) {
        store.values().removeIf(m -> m.getPlanId().equals(planId) && m.getModuleId().equals(moduleId));
    }

    @Override
    public void deleteAllByPlanId(UUID planId) {
        store.values().removeIf(m -> m.getPlanId().equals(planId));
    }
}
