package com.company.bsmsvc.domain.event;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import java.time.Instant;
import java.util.UUID;

public record PaymentMethodAddedEvent(
    UUID paymentMethodId,
    UUID tenantId,
    PaymentProvider paymentProvider,
    String externalPaymentMethodId,
    Instant occurredAt
) {}
