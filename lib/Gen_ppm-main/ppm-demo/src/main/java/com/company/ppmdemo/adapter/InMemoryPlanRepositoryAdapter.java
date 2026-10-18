package com.company.ppmdemo.adapter;

import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * In-memory {@link PlanRepositoryPort} implementation.
 *
 * <p>This is the whole point of ppm-demo: ppm-core's {@code PlanApplicationService}
 * never knows or cares that there is no database behind this port — it depends
 * only on the interface. A real consumer would swap this for a JPA (or any
 * other) adapter without touching a single line of ppm-core.
 */
@Component
public class InMemoryPlanRepositoryAdapter implements PlanRepositoryPort {

    private final Map<UUID, Plan> store = new ConcurrentHashMap<>();
    private final AtomicLong       slugSequence = new AtomicLong(0);

    @Override
    public Plan save(Plan plan) {
        Plan withVersion = Plan.builder()
            .id(plan.getId())
            .version(plan.getVersion() == null ? 0L : plan.getVersion() + 1)
            .code(plan.getCode()).slug(plan.getSlug()).name(plan.getName())
            .tagline(plan.getTagline()).description(plan.getDescription())
            .visibility(plan.getVisibility()).trialDays(plan.getTrialDays())
            .active(plan.isActive()).tier(plan.getTier())
            .createdAt(plan.getCreatedAt() == null ? Instant.now() : plan.getCreatedAt())
            .updatedAt(Instant.now())
            .createdBy(plan.getCreatedBy()).updatedBy(plan.getUpdatedBy())
            .build();
        store.put(withVersion.getId(), withVersion);
        return withVersion;
    }

    @Override
    public Optional<Plan> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Plan> findByCode(String code) {
        return store.values().stream().filter(p -> p.getCode().equals(code)).findFirst();
    }

    @Override
    public Optional<Plan> findBySlug(String slug) {
        return store.values().stream().filter(p -> p.getSlug().equals(slug)).findFirst();
    }

    @Override
    public boolean existsByCode(String code) {
        return store.values().stream().anyMatch(p -> p.getCode().equals(code));
    }

    @Override
    public boolean existsBySlug(String slug) {
        return store.values().stream().anyMatch(p -> p.getSlug().equals(slug));
    }

    @Override
    public long nextSlugSequenceValue() {
        return slugSequence.incrementAndGet();
    }

    @Override
    public List<Plan> findAll() {
        return store.values().stream()
            .sorted((a, b) -> a.getCode().compareTo(b.getCode()))
            .toList();
    }

    @Override
    public void softDelete(UUID id, UUID actorId) {
        store.computeIfPresent(id, (k, existing) -> Plan.builder()
            .id(existing.getId()).version(existing.getVersion() + 1)
            .code(existing.getCode()).slug(existing.getSlug()).name(existing.getName())
            .tagline(existing.getTagline()).description(existing.getDescription())
            .visibility(existing.getVisibility()).trialDays(existing.getTrialDays())
            .active(false).tier(existing.getTier())
            .createdAt(existing.getCreatedAt()).updatedAt(Instant.now())
            .createdBy(existing.getCreatedBy()).updatedBy(actorId)
            .build());
    }
}
