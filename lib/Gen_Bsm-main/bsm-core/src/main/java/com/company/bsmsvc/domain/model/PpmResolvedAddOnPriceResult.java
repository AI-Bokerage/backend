package com.company.bsmsvc.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The {@code data} field from a successful PPM add-on price resolution response.
 *
 * <p>{@code amount} is in major currency units (e.g. 499.00 INR).
 * Callers must convert to minor units before storing in {@code ppm_resolved_price_minor}.
 */
public record PpmResolvedAddOnPriceResult(
    UUID       priceId,
    UUID       addOnId,
    String     cycle,
    String     currency,
    String     region,
    BigDecimal amount,
    boolean    taxInclusive,
    LocalDate  effectiveFrom
) {}
