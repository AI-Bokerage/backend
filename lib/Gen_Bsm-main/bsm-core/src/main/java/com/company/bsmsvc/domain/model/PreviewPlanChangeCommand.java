package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.BillingCycle;
import java.util.UUID;

/** Input to a read-only plan-change preview (proration + change type). */
public record PreviewPlanChangeCommand(
    UUID tenantId,
    UUID targetPpmPlanId,
    String region,
    BillingCycle cycle,
    String promoCode
) {}
