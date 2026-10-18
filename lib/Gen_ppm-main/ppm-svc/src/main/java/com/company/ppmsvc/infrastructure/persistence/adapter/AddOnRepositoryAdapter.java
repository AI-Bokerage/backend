package com.company.ppmsvc.infrastructure.persistence.adapter;

import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.addon.model.AddOn;
import com.company.ppmsvc.addon.port.AddOnRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.mapper.AddOnPersistenceMapper;
import com.company.ppmsvc.infrastructure.persistence.repository.AddOnJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter that implements {@link AddOnRepositoryPort} using
 * Spring Data JPA.
 *
 * <p>This class is the only place in the infrastructure layer that knows
 * about {@link com.company.ppmsvc.infrastructure.persistence.entity.AddOnEntity}.
 * All callers above this boundary (application services) interact exclusively
 * with {@link AddOn} domain objects via the port.
 */
@Component
@RequiredArgsConstructor
public class AddOnRepositoryAdapter implements AddOnRepositoryPort {

    private final AddOnJpaRepository     jpaRepository;
    private final AddOnPersistenceMapper mapper;

    @Override
    public AddOn save(AddOn addOn) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(addOn)));
    }

    @Override
    public Optional<AddOn> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<AddOn> findByCode(String code) {
        return jpaRepository.findByCode(code).map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return jpaRepository.existsByCode(code);
    }

    @Override
    public List<AddOn> findAll() {
        return mapper.toDomainList(jpaRepository.findAllByOrderByCodeAsc());
    }

    @Override
    public void softDelete(UUID id, UUID actorId) {
        int updated = jpaRepository.softDeleteById(id, Instant.now(), actorId);
        if (updated == 0) {
            throw new ResourceNotFoundException(ErrorCode.ADD_ON_NOT_FOUND,
                "Add-on not found or already deleted: " + id);
        }
    }
}
