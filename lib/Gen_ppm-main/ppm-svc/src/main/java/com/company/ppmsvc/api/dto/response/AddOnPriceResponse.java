package com.company.ppmsvc.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for a resolved or retrieved add-on price.
 *
 * <p>The {@code priceId} field carries the UUID of the {@code ppm_add_on_prices} row —
 * named {@code priceId} (rather than {@code id}) so that downstream BSM clients can
 * deserialize it without ambiguity alongside the add-on's own {@code addOnId}.
 */
public record AddOnPriceResponse(
    UUID       priceId,
    UUID       addOnId,
    String     cycle,
    String     currency,
    String     region,
    BigDecimal amount,
    boolean    taxInclusive,
    LocalDate  effectiveFrom
) {}
