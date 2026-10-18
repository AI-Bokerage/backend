package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;

public record CreditNoteCreatedEvent(
    UUID creditNoteId,
    UUID tenantId,
    UUID invoiceId,
    String creditNumber,
    long amountMinor,
    Instant occurredAt
) {}
