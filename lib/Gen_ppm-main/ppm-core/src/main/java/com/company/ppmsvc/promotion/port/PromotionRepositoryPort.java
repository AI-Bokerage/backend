package com.company.ppmsvc.promotion.port;

import com.company.ppmsvc.promotion.model.Promotion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain port for {@link Promotion} persistence. No JPA types cross this
 * interface.
 */
public interface PromotionRepositoryPort {

    /** Persists a new or updated {@link Promotion} and returns the saved state. */
    Promotion save(Promotion promotion);

    /** Returns the promotion with the given ID, or empty if not found (or soft-deleted). */
    Optional<Promotion> findById(UUID id);

    /** Returns all non-deleted promotions ordered by name ascending. */
    List<Promotion> findAll();

    /** Returns all non-deleted promotions belonging to the given campaign, ordered by name ascending. */
    List<Promotion> findByCampaignId(UUID campaignId);

    /**
     * Soft-deletes the promotion with the given ID.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PROMOTION_NOT_FOUND} if no active row exists for the given ID.
     */
    void softDelete(UUID id, UUID actorId);
}
