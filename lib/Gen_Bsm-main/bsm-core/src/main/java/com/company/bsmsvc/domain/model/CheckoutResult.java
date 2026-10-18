package com.company.bsmsvc.domain.model;

import java.util.UUID;

/** Result of the PPM-backed checkout flow. */
public record CheckoutResult(
    UUID subscriptionId,
    UUID invoiceId,
    String checkoutUrl,
    String sessionId,
    long resolvedAmountMinor,
    String currency,
    Long discountAmountMinor,
    UUID ppmPlanVersionId
) {}
