package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentSucceededEvent(
    UUID paymentId,
    UUID invoiceId,
    UUID tenantId,
    long amountMinor,
    Instant occurredAt
) {}
