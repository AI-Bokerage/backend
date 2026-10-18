package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;

public record InvoiceRenewedEvent(
    UUID invoiceId,
    UUID tenantId,
    UUID subscriptionId,
    String invoiceNumber,
    Instant occurredAt
) {}
