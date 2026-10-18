package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InvoicePdfGeneratedEvent {
    private final UUID invoiceId;
    private final UUID tenantId;
    private final String pdfUrl;
    private final Instant occurredAt;
}
