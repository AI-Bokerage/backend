package com.company.ppmsvc.infrastructure.persistence.adapter;

import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.plan.model.PlanVersion;
import com.company.ppmsvc.plan.port.PlanVersionRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.mapper.PlanVersionPersistenceMapper;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanVersionJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter that implements {@link PlanVersionRepositoryPort} using
 * Spring Data JPA.
 *
 * <p>This class is the only place in the infrastructure layer that knows
 * about {@link com.company.ppmsvc.infrastructure.persistence.entity.PlanVersionEntity}.
 * All callers above this boundary (application services) interact exclusively
 * with {@link PlanVersion} domain objects via the port.
 */
@Component
@RequiredArgsConstructor
public class PlanVersionRepositoryAdapter implements PlanVersionRepositoryPort {

    private final PlanVersionJpaRepository    jpaRepository;
    private final PlanVersionPersistenceMapper mapper;

    @Override
    public PlanVersion save(PlanVersion planVersion) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(planVersion)));
    }

    @Override
    public Optional<PlanVersion> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<PlanVersion> findAll() {
        return mapper.toDomainList(jpaRepository.findAllByOrderByPlanIdAscVersionNoDesc());
    }

    @Override
    public List<PlanVersion> findByPlanId(UUID planId) {
        return mapper.toDomainList(jpaRepository.findByPlanId(planId));
    }

    @Override
    public List<PlanVersion> findByPlanIdOrderByVersionNoDesc(UUID planId) {
        return mapper.toDomainList(jpaRepository.findByPlanIdOrderByVersionNoDesc(planId));
    }

    @Override
    public Optional<PlanVersion> findByPlanIdAndVersionNo(UUID planId, Integer versionNo) {
        return jpaRepository.findByPlanIdAndVersionNo(planId, versionNo).map(mapper::toDomain);
    }

    @Override
    public boolean existsByPlanIdAndVersionNo(UUID planId, Integer versionNo) {
        return jpaRepository.existsByPlanIdAndVersionNo(planId, versionNo);
    }

    @Override
    public void softDelete(UUID id, UUID actorId) {
        int updated = jpaRepository.softDeleteById(id, Instant.now(), actorId);
        if (updated == 0) {
            throw new ResourceNotFoundException(ErrorCode.PLAN_VERSION_NOT_FOUND,
                "Plan version not found or already deleted: " + id);
        }
    }
}
