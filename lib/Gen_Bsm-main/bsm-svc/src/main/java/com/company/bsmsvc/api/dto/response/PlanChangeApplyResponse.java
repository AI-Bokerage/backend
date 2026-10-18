package com.company.bsmsvc.api.dto.response;

import com.company.bsmsvc.domain.enums.PpmPlanChangeType;
import java.util.UUID;
import lombok.Builder;

@Builder
public record PlanChangeApplyResponse(
    UUID subscriptionId,
    UUID invoiceId,
    String checkoutUrl,
    String sessionId,
    Long netAmountMinor,
    String currency,
    PpmPlanChangeType changeType,
    UUID ppmPlanVersionId,
    Long promoDiscountMinor,
    Long discountedNetAmountMinor
) {}
