package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.InvoiceStatus;
import java.util.UUID;

public record InvoiceFilter(
    UUID tenantId,
    UUID subscriptionId,
    String invoiceNumber,
    InvoiceStatus status
) {
}
