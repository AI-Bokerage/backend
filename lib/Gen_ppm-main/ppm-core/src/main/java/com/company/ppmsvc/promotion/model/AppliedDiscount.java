package com.company.ppmsvc.promotion.model;

import java.math.BigDecimal;

/**
 * The result of applying a {@link PromotionAction} to a base amount — pure
 * math, no currency (the caller already knows the currency of {@code
 * baseAmount}).
 */
public record AppliedDiscount(
    BigDecimal baseAmount,
    BigDecimal discountAmount,
    BigDecimal finalAmount,
    PromotionAction action
) {
}
