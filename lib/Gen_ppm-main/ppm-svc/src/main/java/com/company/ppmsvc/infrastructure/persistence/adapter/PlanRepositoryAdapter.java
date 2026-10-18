package com.company.ppmsvc.infrastructure.persistence.adapter;

import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.mapper.PlanPersistenceMapper;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter that implements {@link PlanRepositoryPort} using
 * Spring Data JPA.
 *
 * <p>This class is the only place in the infrastructure layer that knows
 * about {@link com.company.ppmsvc.infrastructure.persistence.entity.PlanEntity}.
 * All callers above this boundary (application services) interact exclusively
 * with {@link Plan} domain objects via the port.
 */
@Component
@RequiredArgsConstructor
public class PlanRepositoryAdapter implements PlanRepositoryPort {

    private final PlanJpaRepository    jpaRepository;
    private final PlanPersistenceMapper mapper;

    @Override
    public Plan save(Plan plan) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(plan)));
    }

    @Override
    public Optional<Plan> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Plan> findByCode(String code) {
        return jpaRepository.findByCode(code).map(mapper::toDomain);
    }

    @Override
    public Optional<Plan> findBySlug(String slug) {
        return jpaRepository.findBySlug(slug).map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return jpaRepository.existsByCode(code);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return jpaRepository.existsBySlug(slug);
    }

    @Override
    public long nextSlugSequenceValue() {
        return jpaRepository.nextSlugValue();
    }

    @Override
    public List<Plan> findAll() {
        return mapper.toDomainList(jpaRepository.findAllByOrderByCodeAsc());
    }

    @Override
    public void softDelete(UUID id, UUID actorId) {
        int updated = jpaRepository.softDeleteById(id, Instant.now(), actorId);
        if (updated == 0) {
            throw new ResourceNotFoundException(ErrorCode.PLAN_NOT_FOUND,
                "Plan not found or already deleted: " + id);
        }
    }
}
