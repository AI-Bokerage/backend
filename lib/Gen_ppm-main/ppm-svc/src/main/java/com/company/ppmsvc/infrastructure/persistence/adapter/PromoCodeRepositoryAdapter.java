package com.company.ppmsvc.infrastructure.persistence.adapter;

import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.promocode.model.PromoCode;
import com.company.ppmsvc.promocode.port.PromoCodeRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.mapper.PromoCodePersistenceMapper;
import com.company.ppmsvc.infrastructure.persistence.repository.PromoCodeJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter that implements {@link PromoCodeRepositoryPort} using
 * Spring Data JPA.
 *
 * <p>This class is the only place in the infrastructure layer that knows
 * about {@link com.company.ppmsvc.infrastructure.persistence.entity.PromoCodeEntity}.
 * All callers above this boundary (application services) interact exclusively
 * with {@link PromoCode} domain objects via the port.
 */
@Component
@RequiredArgsConstructor
public class PromoCodeRepositoryAdapter implements PromoCodeRepositoryPort {

    private final PromoCodeJpaRepository    jpaRepository;
    private final PromoCodePersistenceMapper mapper;

    @Override
    public PromoCode save(PromoCode promoCode) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(promoCode)));
    }

    @Override
    public Optional<PromoCode> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<PromoCode> findAll() {
        return mapper.toDomainList(jpaRepository.findAllByOrderByCodeAsc());
    }

    @Override
    public Optional<PromoCode> findByCode(String code) {
        return jpaRepository.findByCode(code).map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return jpaRepository.existsByCode(code);
    }

    @Override
    public void softDelete(UUID id, UUID actorId) {
        int updated = jpaRepository.softDeleteById(id, Instant.now(), actorId);
        if (updated == 0) {
            throw new ResourceNotFoundException(ErrorCode.PROMO_CODE_NOT_FOUND,
                "Promo code not found or already deleted: " + id);
        }
    }
}
