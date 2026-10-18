package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.PpmPlanChangeType;
import java.math.BigDecimal;

/**
 * Output of {@code PpmProrationEngine.calculate()}.
 *
 * <p>All amounts are in the subscription's currency minor units (e.g. paise for INR).
 * {@code fraction} is the ratio of time remaining in the current period — retained for
 * audit/debug purposes and snapshot recording.
 */
public record PpmProrationResult(
    long creditAmountMinor,
    long chargeAmountMinor,
    long netAmountMinor,
    BigDecimal fraction,
    PpmPlanChangeType changeType
) {}
