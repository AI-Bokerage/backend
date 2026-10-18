package com.company.ppmsvc.api.dto.response;

import com.company.ppmsvc.promocode.model.DiscountType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * API response DTO representing a single promo code entry.
 *
 * <p>Serialised {@code type} uses the stable wire value (e.g. {@code "percentage"},
 * {@code "flat"}) via {@link DiscountType}'s {@code @JsonValue} annotation.
 *
 * <p>The following fields are intentionally absent to avoid leaking internal state:
 * {@code version}, {@code deletedAt}, {@code createdBy}, {@code updatedBy}.
 */
public record PromoCodeResponse(

    UUID         id,
    String       code,
    DiscountType type,
    BigDecimal   value,
    LocalDate    validFrom,
    LocalDate    validUntil,
    Integer      usageCap,
    Integer      usageCount,
    Boolean      firstTimeOnly,
    Boolean      active,
    Instant      createdAt,
    Instant      updatedAt
) {}
