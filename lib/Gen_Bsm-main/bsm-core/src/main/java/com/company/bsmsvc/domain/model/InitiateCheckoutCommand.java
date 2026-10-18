package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.BillingCycle;
import java.util.UUID;

/** Input to the PPM-backed checkout flow. */
public record InitiateCheckoutCommand(
    UUID tenantId,
    UUID ppmPlanId,
    BillingCycle billingCycle,
    String region,
    String promoCode,
    String successUrl,
    String cancelUrl,
    String performedBy,
    String reason
) {}
