package com.company.bsmsvc.infrastructure.persistence.mapper;

import com.company.bsmsvc.domain.model.BillingLedgerEntry;
import com.company.bsmsvc.infrastructure.persistence.entity.BillingLedgerEntryEntity;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

@Component
public class BillingLedgerEntryEntityMapper {
    public BillingLedgerEntry toDomain(BillingLedgerEntryEntity e) {
        if (e == null) return null;
        return BillingLedgerEntry.builder()
            .id(e.getId())
            .tenantId(e.getTenantId())
            .subscriptionId(e.getSubscriptionId())
            .invoiceId(e.getInvoiceId())
            .creditNoteId(e.getCreditNoteId())
            .entryType(e.getEntryType())
            .amountMinor(e.getAmountMinor())
            .currency(e.getCurrency())
            .description(e.getDescription())
            .metadata(e.getMetadata())
            .createdAt(e.getCreatedAt())
            .build();
    }

    public BillingLedgerEntryEntity toEntity(BillingLedgerEntry d) {
        if (d == null) return null;
        return BillingLedgerEntryEntity.builder()
            .id(d.getId() == null ? UUID.randomUUID() : d.getId())
            .tenantId(d.getTenantId())
            .subscriptionId(d.getSubscriptionId())
            .invoiceId(d.getInvoiceId())
            .creditNoteId(d.getCreditNoteId())
            .entryType(d.getEntryType())
            .amountMinor(d.getAmountMinor())
            .currency(d.getCurrency())
            .description(d.getDescription())
            .metadata(d.getMetadata())
            .createdAt(d.getCreatedAt() == null ? Instant.now() : d.getCreatedAt())
            .build();
    }
}
