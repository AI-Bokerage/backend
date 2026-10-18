package com.company.ppmsvc.planprice.port;

import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.planprice.model.PlanPrice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain port for {@link PlanPrice} persistence.
 *
 * <p>Application services depend only on this interface; the concrete
 * implementation is supplied by the host application (see INTEGRATION_GUIDE.md in ppm-core).
 *
 * <p>No JPA types cross this interface.
 */
public interface PlanPriceRepositoryPort {

    /**
     * Persists a new or updated {@link PlanPrice} and returns the saved state.
     *
     * <p>Pass {@code version = null} for a new entity so that Spring Data
     * calls {@code persist()} rather than {@code merge()}.
     */
    PlanPrice save(PlanPrice price);

    /** Returns the price with the given ID, or empty if not found (or soft-deleted). */
    Optional<PlanPrice> findById(UUID id);

    /** Returns all non-deleted price rows ordered by {@code planId} ascending. */
    List<PlanPrice> findAll();

    /** Returns all non-deleted price rows for the given plan. */
    List<PlanPrice> findByPlanId(UUID planId);

    /**
     * Returns all non-deleted price rows matching {@code planId + region + currency}.
     * There may be multiple rows when multiple {@code cycle} values or multiple
     * {@code effectiveFrom} dates exist.
     */
    List<PlanPrice> findByPlanIdAndRegionAndCurrency(UUID planId, String region, String currency);

    /**
     * Returns {@code true} if a non-deleted price row already exists for the
     * exact business key {@code (planId, region, currency, cycle, effectiveFrom)}.
     */
    boolean exists(UUID planId, String region, String currency,
                   BillingCycle cycle, LocalDate effectiveFrom);

    /**
     * Soft-deletes the price row with the given ID.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_PRICE_NOT_FOUND} if no active row exists for the given ID.
     */
    void softDelete(UUID id, UUID actorId);
}
