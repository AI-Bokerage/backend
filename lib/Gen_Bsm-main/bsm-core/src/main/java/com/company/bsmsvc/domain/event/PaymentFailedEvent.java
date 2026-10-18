package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailedEvent(
    UUID paymentId,
    UUID invoiceId,
    UUID tenantId,
    String reason,
    Instant occurredAt
) {}
