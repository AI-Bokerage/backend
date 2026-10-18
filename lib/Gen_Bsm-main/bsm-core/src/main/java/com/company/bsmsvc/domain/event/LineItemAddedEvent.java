package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;

public record LineItemAddedEvent(
    UUID invoiceId,
    UUID lineItemId,
    Instant occurredAt
) {}
