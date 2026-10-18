package com.company.ppmsvc.promocodeplan.port;

import com.company.ppmsvc.promocodeplan.model.PromoCodePlan;
import java.util.List;
import java.util.UUID;

/**
 * Domain port for {@link PromoCodePlan} persistence.
 *
 * <p>Application services depend only on this interface; the concrete
 * implementation is supplied by the host application (see INTEGRATION_GUIDE.md in ppm-core).
 *
 * <p>No JPA types cross this interface.
 */
public interface PromoCodePlanRepositoryPort {

    /** Persists a single plan restriction and returns the saved state. */
    PromoCodePlan save(PromoCodePlan mapping);

    /**
     * Persists a batch of plan restrictions in a single database round-trip.
     * The returned list preserves insertion order.
     */
    List<PromoCodePlan> saveAll(List<PromoCodePlan> mappings);

    /** Returns all plan restrictions for the given promo code, in insertion order. */
    List<PromoCodePlan> findByPromoCodeId(UUID promoCodeId);

    /** Returns all promo code restrictions that reference the given plan. */
    List<PromoCodePlan> findByPlanId(UUID planId);

    /** Returns {@code true} if a restriction for {@code (promoCodeId, planId)} already exists. */
    boolean exists(UUID promoCodeId, UUID planId);

    /**
     * Hard-deletes the restriction for {@code (promoCodeId, planId)}.
     * A no-op if no such restriction exists.
     */
    void delete(UUID promoCodeId, UUID planId);

    /**
     * Hard-deletes all plan restrictions for the given promo code.
     * Used when removing all plan-level restrictions from a code.
     */
    void deleteAllByPromoCodeId(UUID promoCodeId);
}
