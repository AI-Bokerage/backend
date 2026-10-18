package com.company.bsmsvc.domain.event;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import java.time.Instant;
import java.util.UUID;

public record PaymentInitiatedEvent(
    UUID paymentId,
    UUID invoiceId,
    UUID tenantId,
    PaymentProvider paymentProvider,
    String externalPaymentId,
    Instant occurredAt
) {}
