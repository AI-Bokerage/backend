package com.company.ppmsvc.coupon.usecase;

import com.company.ppmsvc.coupon.model.Coupon;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for the Coupon catalog.
 *
 * <p>Framework-agnostic — operates exclusively on domain models and
 * primitives. A host application resolves the acting user and translates
 * its own request/response contracts to and from these signatures.
 */
public interface CouponApplicationService {

    /**
     * Creates a new coupon.
     *
     * <p>{@code code} is normalised to uppercase with whitespace stripped.
     * {@code active} defaults to {@code true} when {@code null}.
     *
     * @throws com.company.ppmsvc.exception.BusinessException with
     *         {@code COUPON_CODE_ALREADY_EXISTS} if the normalised code already exists.
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PROMOTION_NOT_FOUND} if {@code promotionId} does not resolve to a promotion.
     */
    Coupon createCoupon(UUID actorId, String code, UUID promotionId, Boolean active);

    /**
     * Partially updates an existing coupon (PATCH semantics). {@code code} is
     * immutable after creation and has no parameter here. A non-null
     * {@code promotionId} re-verifies the referenced promotion exists.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code COUPON_NOT_FOUND} if the coupon does not exist, or
     *         {@code PROMOTION_NOT_FOUND} if the new {@code promotionId} does not resolve.
     */
    Coupon updateCoupon(UUID actorId, UUID id, UUID promotionId, Boolean active);

    /**
     * Returns a single coupon by its UUID.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code COUPON_NOT_FOUND} if the coupon does not exist or is soft-deleted.
     */
    Coupon getCoupon(UUID id);

    /** Looks up a coupon by its code string. */
    Optional<Coupon> getCouponByCode(String code);

    /** Returns all non-deleted coupons, optionally filtered by active flag. */
    List<Coupon> listCoupons(Boolean activeFilter);

    /**
     * Soft-deletes an existing coupon.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code COUPON_NOT_FOUND} if the coupon does not exist or is already deleted.
     */
    void deleteCoupon(UUID actorId, UUID id);
}
