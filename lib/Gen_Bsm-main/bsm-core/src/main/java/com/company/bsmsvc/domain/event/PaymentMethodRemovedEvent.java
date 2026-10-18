package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentMethodRemovedEvent(
    UUID paymentMethodId,
    UUID tenantId,
    String externalPaymentMethodId,
    Instant occurredAt
) {}
