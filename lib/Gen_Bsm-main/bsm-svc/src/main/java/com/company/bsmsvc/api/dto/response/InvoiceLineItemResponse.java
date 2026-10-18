package com.company.bsmsvc.api.dto.response;

import com.company.bsmsvc.domain.enums.InvoiceLineItemType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Invoice line item response")
public record InvoiceLineItemResponse(
    UUID id,
    UUID invoiceId,
    InvoiceLineItemType itemType,
    String description,
    int quantity,
    long unitAmountMinor,
    long amountMinor,
    Map<String, Object> metadata,
    Instant createdAt
) {
}
