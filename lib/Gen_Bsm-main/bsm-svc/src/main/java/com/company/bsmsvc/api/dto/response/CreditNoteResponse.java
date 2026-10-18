package com.company.bsmsvc.api.dto.response;

import com.company.bsmsvc.domain.enums.CreditNoteStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Credit note response payload")
public record CreditNoteResponse(
    UUID id,
    UUID tenantId,
    UUID invoiceId,
    String creditNumber,
    long amountMinor,
    String currency,
    String reason,
    CreditNoteStatus status,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt
) {}
