package com.company.bsmsvc.domain.model;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceCreatedMessage {
    private UUID invoiceId;
    private UUID tenantId;
    private String invoiceNumber;
    private UUID eventId;
    private Instant occurredAt;
    @Builder.Default
    private int eventVersion = 1;
}
