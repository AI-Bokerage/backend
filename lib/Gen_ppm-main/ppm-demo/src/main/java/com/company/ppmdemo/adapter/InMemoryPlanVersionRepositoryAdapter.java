package com.company.ppmdemo.adapter;

import com.company.ppmsvc.plan.model.PlanVersion;
import com.company.ppmsvc.plan.port.PlanVersionRepositoryPort;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory {@link PlanVersionRepositoryPort} implementation.
 *
 * <p>Required because {@code PlanApplicationService} depends on this port too
 * — {@code PlanVersion} is co-located with {@code Plan} in ppm-core (see
 * PACKAGE_GUIDE.md), so any consumer wiring up Plan functionality must also
 * implement this port, even if the demo itself never calls a PlanVersion
 * use-case directly. This is expected coupling from the aggregate design, not
 * integration friction to fix in ppm-core.
 */
@Component
public class InMemoryPlanVersionRepositoryAdapter implements PlanVersionRepositoryPort {

    private final Map<UUID, PlanVersion> store = new ConcurrentHashMap<>();

    @Override
    public PlanVersion save(PlanVersion planVersion) {
        PlanVersion withVersion = PlanVersion.builder()
            .id(planVersion.getId()).version(planVersion.getVersion() == null ? 0L : planVersion.getVersion() + 1)
            .planId(planVersion.getPlanId()).versionNo(planVersion.getVersionNo())
            .effectiveFrom(planVersion.getEffectiveFrom()).effectiveTo(planVersion.getEffectiveTo())
            .active(planVersion.isActive())
            .maxInternalUsers(planVersion.getMaxInternalUsers()).maxClientUsers(planVersion.getMaxClientUsers())
            .maxActiveProjects(planVersion.getMaxActiveProjects()).storageQuotaBytes(planVersion.getStorageQuotaBytes())
            .customDomainEnabled(planVersion.getCustomDomainEnabled()).ssoEnabled(planVersion.getSsoEnabled())
            .prioritySupport(planVersion.getPrioritySupport())
            .createdAt(planVersion.getCreatedAt() == null ? Instant.now() : planVersion.getCreatedAt())
            .updatedAt(Instant.now())
            .createdBy(planVersion.getCreatedBy()).updatedBy(planVersion.getUpdatedBy())
            .build();
        store.put(withVersion.getId(), withVersion);
        return withVersion;
    }

    @Override
    public Optional<PlanVersion> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<PlanVersion> findAll() {
        return store.values().stream()
            .sorted(Comparator.comparing(PlanVersion::getPlanId).thenComparing(Comparator.comparing(PlanVersion::getVersionNo).reversed()))
            .toList();
    }

    @Override
    public List<PlanVersion> findByPlanId(UUID planId) {
        return store.values().stream().filter(v -> v.getPlanId().equals(planId)).toList();
    }

    @Override
    public List<PlanVersion> findByPlanIdOrderByVersionNoDesc(UUID planId) {
        return store.values().stream()
            .filter(v -> v.getPlanId().equals(planId))
            .sorted(Comparator.comparing(PlanVersion::getVersionNo).reversed())
            .toList();
    }

    @Override
    public Optional<PlanVersion> findByPlanIdAndVersionNo(UUID planId, Integer versionNo) {
        return store.values().stream()
            .filter(v -> v.getPlanId().equals(planId) && v.getVersionNo().equals(versionNo))
            .findFirst();
    }

    @Override
    public boolean existsByPlanIdAndVersionNo(UUID planId, Integer versionNo) {
        return store.values().stream()
            .anyMatch(v -> v.getPlanId().equals(planId) && v.getVersionNo().equals(versionNo));
    }

    @Override
    public void softDelete(UUID id, UUID actorId) {
        store.remove(id);
    }
}
