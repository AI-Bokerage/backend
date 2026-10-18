package com.company.ppmsvc.promocode.usecase;

import com.company.ppmsvc.promocode.model.PromoValidationResult;
import java.util.UUID;

/**
 * Promo Validation Engine (PPM-08).
 *
 * <p>Determines whether a promo code may be applied to a specific plan right now
 * and returns discount metadata for valid codes. Never mutates usage counts or
 * any other state — this is a pure read engine.
 *
 * <p>The only exception thrown is {@link com.company.ppmsvc.exception.ResourceNotFoundException}
 * with {@code PLAN_NOT_FOUND} when the requested plan does not exist. All other
 * validation failures are returned as a result with {@code valid=false}.
 */
public interface PromoValidationService {

    /**
     * Validates {@code code} against {@code planId}.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException
     *         with {@code PLAN_NOT_FOUND} if the plan does not exist
     */
    PromoValidationResult validate(String code, UUID planId);
}
