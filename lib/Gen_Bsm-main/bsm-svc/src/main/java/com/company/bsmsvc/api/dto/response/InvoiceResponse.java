package com.company.bsmsvc.api.dto.response;

import com.company.bsmsvc.domain.enums.InvoiceSource;
import com.company.bsmsvc.domain.enums.InvoiceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Invoice response payload")
public record InvoiceResponse(
    UUID id,
    UUID tenantId,
    UUID subscriptionId,
    String invoiceNumber,
    InvoiceStatus status,
    InvoiceSource source,
    long amountDue,
    long amountPaid,
    String currency,
    Instant periodStart,
    Instant periodEnd,
    LocalDate dueDate,
    Instant paidAt,
    Instant createdAt,
    Instant updatedAt,
    List<InvoiceLineItemResponse> lineItems
) {
}
