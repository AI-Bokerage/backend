package com.company.ppmsvc.infrastructure.persistence.adapter;

import com.company.ppmsvc.coupon.model.Coupon;
import com.company.ppmsvc.coupon.port.CouponRepositoryPort;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.infrastructure.persistence.mapper.CouponPersistenceMapper;
import com.company.ppmsvc.infrastructure.persistence.repository.CouponJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter that implements {@link CouponRepositoryPort} using
 * Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class CouponRepositoryAdapter implements CouponRepositoryPort {

    private final CouponJpaRepository    jpaRepository;
    private final CouponPersistenceMapper mapper;

    @Override
    public Coupon save(Coupon coupon) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(coupon)));
    }

    @Override
    public Optional<Coupon> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Coupon> findAll() {
        return mapper.toDomainList(jpaRepository.findAll());
    }

    @Override
    public Optional<Coupon> findByCode(String code) {
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
            throw new ResourceNotFoundException(ErrorCode.COUPON_NOT_FOUND,
                "Coupon not found or already deleted: " + id);
        }
    }
}
