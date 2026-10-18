package com.company.ppmsvc.infrastructure.persistence.adapter;

import com.company.ppmsvc.planmodule.model.PlanModule;
import com.company.ppmsvc.planmodule.port.PlanModuleRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.mapper.PlanModulePersistenceMapper;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanModuleJpaRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter that implements {@link PlanModuleRepositoryPort} using
 * Spring Data JPA.
 *
 * <p>This class is the only place in the infrastructure layer that knows about
 * {@link com.company.ppmsvc.infrastructure.persistence.entity.PlanModuleEntity}.
 * All callers above this boundary interact exclusively with {@link PlanModule}
 * domain objects via the port.
 */
@Component
@RequiredArgsConstructor
public class PlanModuleRepositoryAdapter implements PlanModuleRepositoryPort {

    private final PlanModuleJpaRepository     jpaRepository;
    private final PlanModulePersistenceMapper mapper;

    @Override
    public PlanModule save(PlanModule mapping) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(mapping)));
    }

    @Override
    public List<PlanModule> saveAll(List<PlanModule> mappings) {
        List<com.company.ppmsvc.infrastructure.persistence.entity.PlanModuleEntity> entities =
            mappings.stream().map(mapper::toEntity).toList();
        return mapper.toDomainList(jpaRepository.saveAll(entities));
    }

    @Override
    public List<PlanModule> findByPlanId(UUID planId) {
        return mapper.toDomainList(jpaRepository.findByPlanIdOrderByCreatedAtAsc(planId));
    }

    @Override
    public List<PlanModule> findByModuleId(UUID moduleId) {
        return mapper.toDomainList(jpaRepository.findByModuleId(moduleId));
    }

    @Override
    public boolean exists(UUID planId, UUID moduleId) {
        return jpaRepository.existsByPlanIdAndModuleId(planId, moduleId);
    }

    @Override
    public void delete(UUID planId, UUID moduleId) {
        jpaRepository.deleteByPlanIdAndModuleId(planId, moduleId);
    }

    @Override
    public void deleteAllByPlanId(UUID planId) {
        jpaRepository.deleteAllByPlanId(planId);
    }
}
