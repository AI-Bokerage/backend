package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;

public record CreditNoteVoidedEvent(
    UUID creditNoteId,
    UUID tenantId,
    Instant occurredAt
) {}
