package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;

public record RefundCreatedEvent(
    UUID paymentId,
    UUID invoiceId,
    UUID tenantId,
    long amountMinor,
    String externalRefundId,
    Instant occurredAt
) {}
