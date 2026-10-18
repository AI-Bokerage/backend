package com.company.ppmsvc.promocode.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Outcome reason returned by the Promo Validation Engine (PPM-08).
 *
 * <p>Each constant carries a stable {@code value} used as the JSON wire format.
 * {@link #VALID} is the only success outcome; all others indicate why the code
 * cannot be applied.
 */
@Getter
@RequiredArgsConstructor
public enum PromoValidationReason {

    VALID            ("valid"),
    PROMO_NOT_FOUND  ("promo_not_found"),
    PROMO_INACTIVE   ("promo_inactive"),
    PROMO_EXPIRED    ("promo_expired"),
    PROMO_NOT_STARTED("promo_not_started"),
    USAGE_CAP_REACHED("usage_cap_reached"),
    PLAN_NOT_ELIGIBLE("plan_not_eligible");

    @JsonValue
    private final String value;

    @JsonCreator
    public static PromoValidationReason fromValue(String value) {
        for (PromoValidationReason r : values()) {
            if (r.value.equals(value)) return r;
        }
        throw new IllegalArgumentException("Unknown PromoValidationReason value: " + value);
    }
}
