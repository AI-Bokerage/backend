package com.company.ppmsvc.promocode.usecase;

import com.company.ppmsvc.promocode.model.DiscountType;
import com.company.ppmsvc.promocode.model.PromoCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for the Promo Code Catalog.
 *
 * <p>Framework-agnostic — operates exclusively on domain models and
 * primitives. A host application resolves the acting user and translates
 * its own request/response contracts to and from these signatures.
 */
public interface PromoCodeApplicationService {

    /**
     * Creates a new promo code.
     *
     * <p>Business rules:
     * <ul>
     *   <li>BR-1: Code must be unique among active rows.</li>
     *   <li>BR-2: Code is normalised to uppercase with leading/trailing whitespace stripped.</li>
     *   <li>BR-3: Value must be positive; for PERCENTAGE it must not exceed 100.</li>
     *   <li>BR-4: {@code validUntil} must not be before {@code validFrom}.</li>
     *   <li>BR-5: {@code firstTimeOnly} defaults to {@code false}; {@code active} defaults to
     *             {@code true}; {@code usageCount} is always 0 on create.</li>
     *   <li>BR-7: {@code createdBy} and {@code updatedBy} are set to {@code actorId}.</li>
     * </ul>
     *
     * @throws com.company.ppmsvc.exception.BusinessException with
     *         {@code PROMO_CODE_ALREADY_EXISTS} if the normalised code already exists.
     * @throws com.company.ppmsvc.exception.BusinessException with
     *         {@code VALIDATION_ERROR} if the value or date range is invalid.
     */
    PromoCode createPromoCode(UUID actorId, String code, DiscountType type, BigDecimal value,
                               LocalDate validFrom, LocalDate validUntil, Integer usageCap,
                               Boolean firstTimeOnly, Boolean active);

    /**
     * Partially updates an existing promo code (PATCH semantics).
     *
     * <p>Business rules:
     * <ul>
     *   <li>BR-3: If a new value is supplied, it is re-validated against the effective type.</li>
     *   <li>BR-4: If new dates are supplied, the range is re-validated.</li>
     *   <li>BR-6: {@code id}, {@code code}, {@code createdAt}, {@code createdBy}, and
     *             {@code usageCount} are immutable and always carried forward.</li>
     *   <li>BR-7: {@code updatedBy} and {@code updatedAt} are refreshed.</li>
     * </ul>
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PROMO_CODE_NOT_FOUND} if the promo code does not exist.
     * @throws com.company.ppmsvc.exception.BusinessException with
     *         {@code VALIDATION_ERROR} if the value or date range is invalid.
     */
    PromoCode updatePromoCode(UUID actorId, UUID id, DiscountType type, BigDecimal value,
                              LocalDate validFrom, LocalDate validUntil, Integer usageCap,
                              Boolean firstTimeOnly, Boolean active);

    /**
     * Returns a single promo code by its UUID.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PROMO_CODE_NOT_FOUND} if the promo code does not exist or is soft-deleted.
     */
    PromoCode getPromoCode(UUID id);

    /**
     * Looks up a promo code by its code string.
     *
     * @return the matching {@link PromoCode}, or {@link Optional#empty()} if none found.
     */
    Optional<PromoCode> getPromoCodeByCode(String code);

    /**
     * Returns all non-deleted promo codes matching the supplied filters, ordered by
     * code ascending. A {@code null} filter value means "no filter on that dimension".
     */
    List<PromoCode> listPromoCodes(Boolean active, DiscountType type);

    /**
     * Soft-deletes an existing promo code.
     *
     * <p>Business rules:
     * <ul>
     *   <li>BR-8: Delegates to the repository soft-delete; the repository throws
     *             {@code PROMO_CODE_NOT_FOUND} if the row is absent or already deleted.</li>
     *   <li>BR-7: {@code actorId} is passed to the repository for audit.</li>
     * </ul>
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PROMO_CODE_NOT_FOUND} if the promo code does not exist or is already deleted.
     */
    void deletePromoCode(UUID actorId, UUID id);
}
