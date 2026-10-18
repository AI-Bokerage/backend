package com.company.bsmsvc.storage;

import com.company.bsmsvc.domain.model.PlatformInvoice;
import org.springframework.stereotype.Component;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class DefaultInvoiceStorageKeyGenerator implements InvoiceStorageKeyGenerator {

    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy").withZone(ZoneOffset.UTC);

    @Override
    public String generateKey(PlatformInvoice invoice) {
        String year = invoice.getCreatedAt() != null
            ? YEAR_FORMATTER.format(invoice.getCreatedAt())
            : YEAR_FORMATTER.format(java.time.Instant.now());
        String tenantId = sanitize(invoice.getTenantId().toString());
        String invoiceNumber = sanitize(invoice.getInvoiceNumber());
        return String.format("invoices/%s/%s/%s.pdf", tenantId, year, invoiceNumber);
    }

    private String sanitize(String value) {
        if (value == null) {
            return "unknown";
        }
        return value.trim().replaceAll("[^A-Za-z0-9_.-]", "-").replaceAll("-+", "-");
    }
}
