package com.company.ppmsvc.infrastructure.persistence.adapter;

import com.company.ppmsvc.planaddon.model.PlanAddOn;
import com.company.ppmsvc.planaddon.port.PlanAddOnRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.mapper.PlanAddOnPersistenceMapper;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanAddOnJpaRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter that implements {@link PlanAddOnRepositoryPort} using
 * Spring Data JPA.
 *
 * <p>This class is the only place in the infrastructure layer that knows about
 * {@link com.company.ppmsvc.infrastructure.persistence.entity.PlanAddOnEntity}.
 * All callers above this boundary interact exclusively with {@link PlanAddOn}
 * domain objects via the port.
 */
@Component
@RequiredArgsConstructor
public class PlanAddOnRepositoryAdapter implements PlanAddOnRepositoryPort {

    private final PlanAddOnJpaRepository     jpaRepository;
    private final PlanAddOnPersistenceMapper mapper;

    @Override
    public PlanAddOn save(PlanAddOn mapping) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(mapping)));
    }

    @Override
    public List<PlanAddOn> saveAll(List<PlanAddOn> mappings) {
        List<com.company.ppmsvc.infrastructure.persistence.entity.PlanAddOnEntity> entities =
            mappings.stream().map(mapper::toEntity).toList();
        return mapper.toDomainList(jpaRepository.saveAll(entities));
    }

    @Override
    public List<PlanAddOn> findByPlanId(UUID planId) {
        return mapper.toDomainList(jpaRepository.findByPlanIdOrderByCreatedAtAsc(planId));
    }

    @Override
    public List<PlanAddOn> findByAddOnId(UUID addOnId) {
        return mapper.toDomainList(jpaRepository.findByAddOnId(addOnId));
    }

    @Override
    public boolean exists(UUID planId, UUID addOnId) {
        return jpaRepository.existsByPlanIdAndAddOnId(planId, addOnId);
    }

    @Override
    public void delete(UUID planId, UUID addOnId) {
        jpaRepository.deleteByPlanIdAndAddOnId(planId, addOnId);
    }

    @Override
    public void deleteAllByPlanId(UUID planId) {
        jpaRepository.deleteAllByPlanId(planId);
    }
}
