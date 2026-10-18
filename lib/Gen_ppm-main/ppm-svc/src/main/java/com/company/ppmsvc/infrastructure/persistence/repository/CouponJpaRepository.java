package com.company.ppmsvc.infrastructure.persistence.repository;

import com.company.ppmsvc.infrastructure.persistence.entity.CouponEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository for {@link CouponEntity}.
 *
 * <p>Application services must not use this directly — they depend on
 * {@link com.company.ppmsvc.coupon.port.CouponRepositoryPort}.
 */
public interface CouponJpaRepository extends JpaRepository<CouponEntity, UUID> {

    /** Returns the non-deleted coupon with the given code, or empty. */
    Optional<CouponEntity> findByCode(String code);

    /** Returns {@code true} if a non-deleted coupon with the given code exists. */
    boolean existsByCode(String code);

    /**
     * Soft-deletes a coupon row by setting {@code deleted_at}, {@code updated_at},
     * and {@code updated_by} in a single atomic UPDATE.
     */
    @Transactional
    @Modifying
    @Query("UPDATE CouponEntity c SET c.deletedAt = :now, c.updatedAt = :now, c.updatedBy = :actorId " +
           "WHERE c.id = :id AND c.deletedAt IS NULL")
    int softDeleteById(@Param("id") UUID id,
                       @Param("now") Instant now,
                       @Param("actorId") UUID actorId);
}
