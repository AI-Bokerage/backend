package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InvoicePdfGenerationFailedEvent {
    private final UUID invoiceId;
    private final UUID tenantId;
    private final Instant occurredAt;
}
