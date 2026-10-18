package com.company.ppmsvc.api.dto.response;

import com.company.ppmsvc.common.BillingCycle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response from the Pricing Resolver Engine (PPM-09).
 *
 * <p>Carries the single applicable price row selected by the resolver according
 * to the active + effective-date + cycle rules.  All fields are always populated
 * on a successful resolution (HTTP 200).
 */
public record ResolvedPriceResponse(

    UUID         planId,
    UUID         priceId,
    BillingCycle cycle,
    String       currency,
    String       region,
    BigDecimal   amount,
    boolean      taxInclusive,
    LocalDate    effectiveFrom,
    boolean      active,
    Long         version
) {}
