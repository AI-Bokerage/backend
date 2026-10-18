package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;

public record InvoiceMarkedPaidEvent(
    UUID invoiceId,
    UUID tenantId,
    Instant occurredAt
) {}
