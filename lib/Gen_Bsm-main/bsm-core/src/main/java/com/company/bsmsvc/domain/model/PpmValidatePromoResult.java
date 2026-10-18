package com.company.bsmsvc.domain.model;

import java.math.BigDecimal;

/**
 * The {@code data} field from a successful PPM promo-validate response.
 *
 * <p>When {@code valid=false} the discount fields ({@code discountType},
 * {@code discountValue}) will be {@code null} — JSON {@code @JsonInclude(NON_NULL)}
 * on the PPM side omits them on invalid responses.
 *
 * <p>Wire values for {@code discountType}: {@code "percentage"}, {@code "flat"}.
 * Wire values for {@code reason}: {@code "valid"}, {@code "promo_expired"},
 * {@code "promo_not_started"}, {@code "promo_inactive"}, {@code "usage_cap_reached"},
 * {@code "plan_not_eligible"}, {@code "promo_not_found"}.
 */
public record PpmValidatePromoResult(
    boolean    valid,
    String     code,
    String     discountType,
    BigDecimal discountValue,
    String     reason,
    boolean    firstTimeOnly
) {}
