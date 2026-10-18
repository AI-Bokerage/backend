package com.company.ppmsvc.infrastructure.persistence.adapter;

import com.company.ppmsvc.module.model.ModuleCode;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.module.model.Module;
import com.company.ppmsvc.module.port.ModuleRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.mapper.ModulePersistenceMapper;
import com.company.ppmsvc.infrastructure.persistence.repository.ModuleJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter that implements {@link ModuleRepositoryPort} using
 * Spring Data JPA.
 *
 * <p>This class is the only place in the infrastructure layer that knows
 * about {@link com.company.ppmsvc.infrastructure.persistence.entity.ModuleEntity}.
 * All callers above this boundary (application services) interact exclusively
 * with {@link Module} domain objects via the port.
 */
@Component
@RequiredArgsConstructor
public class ModuleRepositoryAdapter implements ModuleRepositoryPort {

    private final ModuleJpaRepository    jpaRepository;
    private final ModulePersistenceMapper mapper;

    @Override
    public Module save(Module module) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(module)));
    }

    @Override
    public Optional<Module> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Module> findByCode(ModuleCode code) {
        return jpaRepository.findByCode(code).map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(ModuleCode code) {
        return jpaRepository.existsByCode(code);
    }

    @Override
    public List<Module> findAll() {
        return mapper.toDomainList(jpaRepository.findAllByOrderByCodeAsc());
    }

    @Override
    public void softDelete(UUID id, UUID actorId) {
        int updated = jpaRepository.softDeleteById(id, Instant.now(), actorId);
        if (updated == 0) {
            throw new ResourceNotFoundException(ErrorCode.MODULE_NOT_FOUND,
                "Module not found or already deleted: " + id);
        }
    }
}
