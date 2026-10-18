package com.company.ppmsvc.infrastructure.persistence.repository;

import com.company.ppmsvc.infrastructure.persistence.entity.PromoCodeEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository for {@link PromoCodeEntity}.
 *
 * <p>This interface is an infrastructure detail.  Application services must
 * not use it directly — they depend on
 * {@link com.company.ppmsvc.promocode.port.PromoCodeRepositoryPort}, whose
 * implementation delegates to this repository via the persistence adapter.
 *
 * <p>All derived-query methods respect the {@code @SQLRestriction("deleted_at IS NULL")}
 * declared on {@link PromoCodeEntity}, so only non-deleted rows are returned.
 */
public interface PromoCodeJpaRepository extends JpaRepository<PromoCodeEntity, UUID> {

    /** Returns the non-deleted promo code with the given code string, or empty. */
    Optional<PromoCodeEntity> findByCode(String code);

    /** Returns {@code true} if a non-deleted promo code with the given code string exists. */
    boolean existsByCode(String code);

    /** Returns all non-deleted promo code rows ordered by code ascending. */
    List<PromoCodeEntity> findAllByOrderByCodeAsc();

    /**
     * Soft-deletes a promo code row by setting {@code deleted_at}, {@code updated_at},
     * and {@code updated_by} in a single atomic UPDATE.
     *
     * <p>Returns the number of rows affected (0 = not found or already deleted).
     */
    @Transactional
    @Modifying
    @Query("UPDATE PromoCodeEntity p SET p.deletedAt = :now, p.updatedAt = :now, p.updatedBy = :actorId " +
           "WHERE p.id = :id AND p.deletedAt IS NULL")
    int softDeleteById(@Param("id") UUID id,
                       @Param("now") Instant now,
                       @Param("actorId") UUID actorId);
}
