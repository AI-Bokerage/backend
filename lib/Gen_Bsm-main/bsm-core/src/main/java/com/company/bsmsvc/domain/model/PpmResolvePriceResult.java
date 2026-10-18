package com.company.bsmsvc.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The {@code data} field from a successful PPM price-resolve response.
 *
 * <p>{@code amount} is in major currency units (e.g. 999.00 INR).
 * Callers must convert to minor units before storing or comparing with BSM price fields.
 */
public record PpmResolvePriceResult(
    UUID       planId,
    UUID       priceId,
    String     cycle,
    String     currency,
    String     region,
    BigDecimal amount,
    boolean    taxInclusive,
    LocalDate  effectiveFrom,
    boolean    active,
    Long       version
) {}
