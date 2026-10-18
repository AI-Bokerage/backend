package com.company.ppmsvc.api.dto.request;

import com.company.ppmsvc.promocode.model.DiscountType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for partially updating an existing promo code (PATCH semantics).
 *
 * <p>A {@code null} value means "leave unchanged".
 *
 * <p>{@code code} is intentionally absent — it is immutable after creation (BR-6).
 * {@code usageCount} is also absent — it is never updated by client requests.
 */
public record UpdatePromoCodeRequest(

    DiscountType type,
    BigDecimal   value,
    LocalDate    validFrom,
    LocalDate    validUntil,
    Integer      usageCap,
    Boolean      firstTimeOnly,
    Boolean      active
) {}
