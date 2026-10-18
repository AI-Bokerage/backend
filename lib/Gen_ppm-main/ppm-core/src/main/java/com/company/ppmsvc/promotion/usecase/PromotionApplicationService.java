package com.company.ppmsvc.promotion.usecase;

import com.company.ppmsvc.promotion.model.Promotion;
import com.company.ppmsvc.promotion.model.PromotionAction;
import com.company.ppmsvc.promotion.model.PromotionCondition;
import com.company.ppmsvc.promotion.model.PromotionSource;
import com.company.ppmsvc.promotion.model.PromotionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Application service for the Promotion catalog.
 *
 * <p>Framework-agnostic — operates exclusively on domain models and
 * primitives. A host application resolves the acting user and translates
 * its own request/response contracts to and from these signatures.
 */
public interface PromotionApplicationService {

    /**
     * Creates a new promotion.
     *
     * <p>Business rules:
     * <ul>
     *   <li>Date range: {@code validUntil} must not be before {@code validFrom}.</li>
     *   <li>Action: PERCENTAGE must be in (0,100]; caps must be &gt; 0 and max ≥ min if both set.
     *                FLAT must be &gt; 0.</li>
     *   <li>{@code status} defaults to {@link PromotionStatus#ACTIVE} when null.</li>
     *   <li>{@code conditions} defaults to an empty list when null. No element may be null.</li>
     *   <li>{@code usageCapPerUser} must be ≥ 1 when set; {@code null} means unlimited.</li>
     *   <li>{@code source} defaults to {@link PromotionSource#NORMAL} when null; any value is valid.</li>
     *   <li>{@code campaignId} is optional; when non-null, the referenced campaign must exist.</li>
     * </ul>
     *
     * @throws com.company.ppmsvc.exception.BusinessException with
     *         {@code VALIDATION_ERROR} if the date range or action is invalid, or
     *         {@code CONDITION_VALIDATION_ERROR} if a condition is malformed.
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code CAMPAIGN_NOT_FOUND} if a non-null {@code campaignId} does not resolve.
     */
    Promotion createPromotion(UUID actorId, String name, String description, PromotionAction action,
                              LocalDate validFrom, LocalDate validUntil, PromotionStatus status,
                              PromotionSource source, UUID campaignId, List<PromotionCondition> conditions,
                              Integer usageCapPerUser);

    /**
     * Partially updates an existing promotion (PATCH semantics). A {@code null}
     * argument means "leave unchanged" for every field <em>except</em> {@code
     * campaignId}: since {@code campaignId} is itself nullable domain state,
     * {@code null} there means "remove from campaign / no campaign" and is
     * always applied (no existence check needed); a non-null value is verified
     * to exist and replaces the existing one. A non-null {@code action}
     * replaces the existing one and is revalidated; a non-null {@code
     * conditions} replaces the existing list and is revalidated.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PROMOTION_NOT_FOUND} if the promotion does not exist, or
     *         {@code CAMPAIGN_NOT_FOUND} if a non-null {@code campaignId} does not resolve.
     * @throws com.company.ppmsvc.exception.BusinessException with
     *         {@code VALIDATION_ERROR} or {@code CONDITION_VALIDATION_ERROR} if the
     *         resulting state is invalid.
     */
    Promotion updatePromotion(UUID actorId, UUID id, String name, String description, PromotionAction action,
                              LocalDate validFrom, LocalDate validUntil, PromotionStatus status,
                              PromotionSource source, UUID campaignId, List<PromotionCondition> conditions,
                              Integer usageCapPerUser);

    /**
     * Returns a single promotion by its UUID.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PROMOTION_NOT_FOUND} if the promotion does not exist or is soft-deleted.
     */
    Promotion getPromotion(UUID id);

    /**
     * Returns all non-deleted promotions, optionally filtered by status.
     * A {@code null} filter means "no filter".
     */
    List<Promotion> listPromotions(PromotionStatus statusFilter);

    /**
     * Soft-deletes an existing promotion.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PROMOTION_NOT_FOUND} if the promotion does not exist or is already deleted.
     */
    void deletePromotion(UUID actorId, UUID id);
}
