package com.company.bsmsvc.domain.model.payment;

import java.util.Map;

public record CreateCheckoutSessionCommand(
    String externalCustomerId,
    String successUrl,
    String cancelUrl,
    long amountMinor,
    String currency,
    String description,
    Map<String, String> metadata
) {}
