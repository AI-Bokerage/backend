package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.PpmPlanChangeType;
import java.util.UUID;

/** Result of a read-only plan-change preview. */
public record PlanChangePreviewResult(
    UUID subscriptionId,
    UUID currentPpmPlanId,
    Long currentPpmResolvedPriceMinor,
    UUID targetPpmPlanId,
    UUID targetPpmPriceId,
    UUID targetPpmPlanVersionId,
    Long targetPpmResolvedPriceMinor,
    Long prorationCreditMinor,
    Long prorationChargeMinor,
    Long prorationNetMinor,
    String currency,
    PpmPlanChangeType changeType,
    Long promoDiscountMinor,
    Long discountedNetAmountMinor
) {}
