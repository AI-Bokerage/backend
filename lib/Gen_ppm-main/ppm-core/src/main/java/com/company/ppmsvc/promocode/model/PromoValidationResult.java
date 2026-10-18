package com.company.ppmsvc.promocode.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Result of the Promo Validation Engine (PPM-08).
 *
 * <p>{@code valid=true} means the code may be applied to the requested plan;
 * {@code valid=false} means it cannot, and {@code reason} explains why.
 * Discount fields are {@code null} on invalid results.
 */
public record PromoValidationResult(
    boolean valid,
    String code,
    UUID promoCodeId,
    DiscountType discountType,
    BigDecimal discountValue,
    PromoValidationReason reason,
    LocalDate validUntil,
    boolean firstTimeOnly
) {}
