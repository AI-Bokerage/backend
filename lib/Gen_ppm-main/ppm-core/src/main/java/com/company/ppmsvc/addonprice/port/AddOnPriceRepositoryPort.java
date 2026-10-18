package com.company.ppmsvc.addonprice.port;

import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.addonprice.model.AddOnPrice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain port for {@link AddOnPrice} persistence.
 *
 * <p>Application services depend only on this interface; the concrete
 * implementation is supplied by the host application (see INTEGRATION_GUIDE.md in ppm-core).
 *
 * <p>No JPA types cross this interface.
 */
public interface AddOnPriceRepositoryPort {

    /**
     * Persists a new or updated {@link AddOnPrice} and returns the saved state.
     *
     * <p>Pass {@code version = null} for a new entity so that Spring Data
     * calls {@code persist()} rather than {@code merge()}.
     */
    AddOnPrice save(AddOnPrice price);

    /** Returns the price with the given ID, or empty if not found (or soft-deleted). */
    Optional<AddOnPrice> findById(UUID id);

    /** Returns all non-deleted price rows ordered by {@code addOnId} ascending. */
    List<AddOnPrice> findAll();

    /** Returns all non-deleted price rows for the given add-on. */
    List<AddOnPrice> findByAddOnId(UUID addOnId);

    /**
     * Returns all non-deleted price rows matching {@code addOnId + region + currency}.
     * There may be multiple rows when multiple {@code cycle} values or multiple
     * {@code effectiveFrom} dates exist.
     */
    List<AddOnPrice> findByAddOnIdAndRegionAndCurrency(UUID addOnId, String region, String currency);

    /**
     * Returns {@code true} if a non-deleted price row already exists for the
     * exact business key {@code (addOnId, region, currency, cycle, effectiveFrom)}.
     */
    boolean exists(UUID addOnId, String region, String currency,
                   BillingCycle cycle, LocalDate effectiveFrom);

    /**
     * Returns the most recently effective active price for the given combination,
     * where {@code effectiveFrom} is on or before today.  Returns empty when no
     * qualifying active row exists.
     */
    Optional<AddOnPrice> findActivePrice(UUID addOnId, String region, String currency, BillingCycle cycle);

    /**
     * Soft-deletes the price row with the given ID.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code ADD_ON_PRICE_NOT_FOUND} if no active row exists for the given ID.
     */
    void softDelete(UUID id, UUID actorId);
}
