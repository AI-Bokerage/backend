package com.company.bsmsvc.domain.model.payment;

import java.util.Map;
import java.util.UUID;

public record CreatePaymentIntentCommand(
    UUID tenantId,
    String externalCustomerId,
    long amountMinor,
    String currency,
    Map<String, String> metadata
) {}
