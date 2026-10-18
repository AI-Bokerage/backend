package com.company.bsmsvc.api.dto.response;

import com.company.bsmsvc.domain.enums.PpmPlanChangeType;
import java.util.UUID;

public record PlanChangePreviewResponse(
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
