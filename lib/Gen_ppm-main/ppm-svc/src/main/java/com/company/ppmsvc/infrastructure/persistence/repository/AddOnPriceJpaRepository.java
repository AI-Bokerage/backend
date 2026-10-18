package com.company.ppmsvc.infrastructure.persistence.repository;

import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.infrastructure.persistence.entity.AddOnPriceEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository for {@link AddOnPriceEntity}.
 *
 * <p>This interface is an infrastructure detail.  Application services must
 * not use it directly — they depend on
 * {@link com.company.ppmsvc.addonprice.port.AddOnPriceRepositoryPort}, whose
 * implementation delegates to this repository via the persistence adapter.
 *
 * <p>All derived-query methods respect the {@code @SQLRestriction("deleted_at IS NULL")}
 * declared on {@link AddOnPriceEntity}, so only non-deleted rows are returned.
 */
public interface AddOnPriceJpaRepository extends JpaRepository<AddOnPriceEntity, UUID> {

    /** Returns all non-deleted price rows for the given add-on, in insertion order. */
    List<AddOnPriceEntity> findByAddOnId(UUID addOnId);

    /**
     * Returns all non-deleted price rows matching the given add-on, region, and currency.
     * Multiple rows are expected when different cycles or effective dates exist.
     */
    List<AddOnPriceEntity> findByAddOnIdAndRegionAndCurrency(
            UUID addOnId, String region, String currency);

    /**
     * Returns {@code true} if a non-deleted price row exists for the exact
     * business key {@code (addOnId, region, currency, cycle, effectiveFrom)}.
     */
    boolean existsByAddOnIdAndRegionAndCurrencyAndCycleAndEffectiveFrom(
            UUID addOnId, String region, String currency,
            BillingCycle cycle, LocalDate effectiveFrom);

    /**
     * Returns the most recently effective active price row matching the given
     * add-on, region, currency, and cycle, where {@code effectiveFrom <= asOf}.
     * Returns empty when no qualifying row exists.
     */
    Optional<AddOnPriceEntity> findFirstByAddOnIdAndRegionAndCurrencyAndCycleAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            UUID addOnId, String region, String currency, BillingCycle cycle, LocalDate asOf);

    /** Returns all non-deleted price rows ordered by {@code addOnId} ascending. */
    List<AddOnPriceEntity> findAllByOrderByAddOnIdAsc();

    /**
     * Soft-deletes a price row by setting {@code deleted_at}, {@code updated_at},
     * and {@code updated_by} in a single atomic UPDATE.
     *
     * <p>Returns the number of rows affected (0 = not found or already deleted).
     */
    @Transactional
    @Modifying
    @Query("UPDATE AddOnPriceEntity p SET p.deletedAt = :now, p.updatedAt = :now, p.updatedBy = :actorId " +
           "WHERE p.id = :id AND p.deletedAt IS NULL")
    int softDeleteById(@Param("id") UUID id,
                       @Param("now") Instant now,
                       @Param("actorId") UUID actorId);
}
