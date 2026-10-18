package com.company.ppmsvc.promotion.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Outcome reason returned by {@link
 * com.company.ppmsvc.promotion.usecase.PromotionPricingService}.
 *
 * <p>{@link #VALID} is the only success outcome; all others explain why no
 * discount was applied. Only a missing plan/price is an exception (404) —
 * every other outcome, including a missing coupon, is a result.
 */
@Getter
@RequiredArgsConstructor
public enum PromotionApplicationReason {

    VALID                 ("valid"),
    NO_COUPON             ("no_coupon"),
    COUPON_NOT_FOUND      ("coupon_not_found"),
    COUPON_INACTIVE       ("coupon_inactive"),
    PROMOTION_INACTIVE    ("promotion_inactive"),
    PROMOTION_NOT_STARTED ("promotion_not_started"),
    PROMOTION_EXPIRED     ("promotion_expired"),
    PLAN_NOT_ELIGIBLE     ("plan_not_eligible"),
    ELIGIBILITY_VIOLATION ("eligibility_violation"),
    USAGE_LIMIT_PER_USER  ("usage_limit_per_user"),
    REFERRAL_CODE_NOT_FOUND     ("referral_code_not_found"),
    REFERRAL_CODE_INACTIVE      ("referral_code_inactive"),
    REFERRAL_PROGRAM_INACTIVE   ("referral_program_inactive");

    @JsonValue
    private final String value;

    @JsonCreator
    public static PromotionApplicationReason fromValue(String value) {
        for (PromotionApplicationReason r : values()) {
            if (r.value.equals(value)) return r;
        }
        throw new IllegalArgumentException("Unknown PromotionApplicationReason value: " + value);
    }
}
