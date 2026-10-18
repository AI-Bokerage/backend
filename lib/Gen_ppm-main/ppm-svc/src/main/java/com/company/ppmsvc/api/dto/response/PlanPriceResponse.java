package com.company.ppmsvc.api.dto.response;

import com.company.ppmsvc.common.BillingCycle;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * API response DTO representing a single plan price entry.
 *
 * <p>Serialised {@code cycle} uses the stable wire value (e.g. {@code "monthly"})
 * via {@link BillingCycle}'s {@code @JsonValue} annotation.
 */
public record PlanPriceResponse(

    UUID         id,
    UUID         planId,
    BillingCycle cycle,
    String       currency,
    String       region,
    BigDecimal   amount,
    boolean      taxInclusive,
    LocalDate    effectiveFrom,
    boolean      active,
    Instant      createdAt,
    Instant      updatedAt
) {}
