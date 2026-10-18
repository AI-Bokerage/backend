package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.BillingCycle;
import java.util.UUID;

/** Input to purchasing a PPM-backed subscription add-on. */
public record PurchaseAddOnCommand(
    UUID tenantId,
    UUID ppmAddOnId,
    String region,
    BillingCycle cycle,
    String successUrl,
    String cancelUrl,
    String performedBy
) {}
