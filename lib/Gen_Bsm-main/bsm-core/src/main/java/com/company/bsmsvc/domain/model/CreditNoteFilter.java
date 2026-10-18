package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.CreditNoteStatus;
import java.time.Instant;
import java.util.UUID;

public record CreditNoteFilter(
    UUID tenantId,
    UUID invoiceId,
    CreditNoteStatus status,
    String creditNumber,
    Instant createdFrom,
    Instant createdTo
) {}
