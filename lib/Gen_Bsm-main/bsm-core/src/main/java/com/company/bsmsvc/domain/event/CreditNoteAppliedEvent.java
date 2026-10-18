package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;

public record CreditNoteAppliedEvent(
    UUID creditNoteId,
    UUID tenantId,
    UUID invoiceId,
    Instant occurredAt
) {}
