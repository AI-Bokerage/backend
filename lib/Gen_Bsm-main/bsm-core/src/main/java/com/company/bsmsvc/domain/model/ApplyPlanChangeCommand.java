package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.BillingCycle;
import java.util.UUID;

/** Input to applying a PPM plan change atomically. */
public record ApplyPlanChangeCommand(
    UUID tenantId,
    UUID targetPpmPlanId,
    String region,
    BillingCycle cycle,
    String reason,
    String performedBy,
    String successUrl,
    String cancelUrl,
    String promoCode
) {}
