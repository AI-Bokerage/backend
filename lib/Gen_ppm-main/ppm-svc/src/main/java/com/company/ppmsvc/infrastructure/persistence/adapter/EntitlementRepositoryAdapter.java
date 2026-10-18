package com.company.ppmsvc.infrastructure.persistence.adapter;

import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.entitlement.model.Entitlement;
import com.company.ppmsvc.entitlement.port.EntitlementRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.mapper.EntitlementPersistenceMapper;
import com.company.ppmsvc.infrastructure.persistence.repository.EntitlementJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter that implements {@link EntitlementRepositoryPort} using
 * Spring Data JPA.
 *
 * <p>This class is the only place in the infrastructure layer that knows
 * about {@link com.company.ppmsvc.infrastructure.persistence.entity.EntitlementEntity}.
 * All callers above this boundary (application services) interact exclusively
 * with {@link Entitlement} domain objects via the port.
 */
@Component
@RequiredArgsConstructor
public class EntitlementRepositoryAdapter implements EntitlementRepositoryPort {

    private final EntitlementJpaRepository    jpaRepository;
    private final EntitlementPersistenceMapper mapper;

    @Override
    public Entitlement save(Entitlement entitlement) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(entitlement)));
    }

    @Override
    public Optional<Entitlement> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Entitlement> findByCode(String code) {
        return jpaRepository.findByCode(code).map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return jpaRepository.existsByCode(code);
    }

    @Override
    public List<Entitlement> findAll() {
        return mapper.toDomainList(jpaRepository.findAllByOrderByCodeAsc());
    }

    @Override
    public List<Entitlement> findAllById(Set<UUID> ids) {
        return mapper.toDomainList(jpaRepository.findAllById(ids));
    }

    @Override
    public void softDelete(UUID id, UUID actorId) {
        int updated = jpaRepository.softDeleteById(id, Instant.now(), actorId);
        if (updated == 0) {
            throw new ResourceNotFoundException(ErrorCode.ENTITLEMENT_NOT_FOUND,
                "Entitlement not found or already deleted: " + id);
        }
    }
}
