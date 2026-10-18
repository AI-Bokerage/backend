package com.company.bsmsvc.domain.model.payment;

import java.util.Map;

public record RetryPaymentCommand(
    String externalCustomerId,
    String paymentMethodId,
    long amountMinor,
    String currency,
    Map<String, String> metadata,
    /**
     * Deterministic Stripe idempotency key derived from the dunning attempt ID.
     * Retrying the same attempt after an OLE rollback re-uses the same PI on Stripe
     * instead of creating a new charge — preventing double billing.
     */
    String idempotencyKey
) {}
