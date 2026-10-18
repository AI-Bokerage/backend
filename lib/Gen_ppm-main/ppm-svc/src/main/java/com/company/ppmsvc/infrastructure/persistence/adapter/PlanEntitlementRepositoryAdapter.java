package com.company.ppmsvc.infrastructure.persistence.adapter;

import com.company.ppmsvc.planentitlement.model.PlanEntitlement;
import com.company.ppmsvc.planentitlement.port.PlanEntitlementRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.mapper.PlanEntitlementPersistenceMapper;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanEntitlementJpaRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter that implements {@link PlanEntitlementRepositoryPort} using
 * Spring Data JPA.
 *
 * <p>This class is the only place in the infrastructure layer that knows about
 * {@link com.company.ppmsvc.infrastructure.persistence.entity.PlanEntitlementEntity}.
 * All callers above this boundary interact exclusively with {@link PlanEntitlement}
 * domain objects via the port.
 */
@Component
@RequiredArgsConstructor
public class PlanEntitlementRepositoryAdapter implements PlanEntitlementRepositoryPort {

    private final PlanEntitlementJpaRepository     jpaRepository;
    private final PlanEntitlementPersistenceMapper mapper;

    @Override
    public PlanEntitlement save(PlanEntitlement assignment) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(assignment)));
    }

    @Override
    public List<PlanEntitlement> saveAll(List<PlanEntitlement> assignments) {
        List<com.company.ppmsvc.infrastructure.persistence.entity.PlanEntitlementEntity> entities =
            assignments.stream().map(mapper::toEntity).toList();
        return mapper.toDomainList(jpaRepository.saveAll(entities));
    }

    @Override
    public List<PlanEntitlement> findByPlanId(UUID planId) {
        return mapper.toDomainList(jpaRepository.findByPlanIdOrderByCreatedAtAsc(planId));
    }

    @Override
    public List<PlanEntitlement> findByEntitlementId(UUID entitlementId) {
        return mapper.toDomainList(jpaRepository.findByEntitlementId(entitlementId));
    }

    @Override
    public boolean exists(UUID planId, UUID entitlementId) {
        return jpaRepository.existsByPlanIdAndEntitlementId(planId, entitlementId);
    }

    @Override
    public void delete(UUID planId, UUID entitlementId) {
        jpaRepository.deleteByPlanIdAndEntitlementId(planId, entitlementId);
    }

    @Override
    public void deleteAllByPlanId(UUID planId) {
        jpaRepository.deleteAllByPlanId(planId);
    }
}
