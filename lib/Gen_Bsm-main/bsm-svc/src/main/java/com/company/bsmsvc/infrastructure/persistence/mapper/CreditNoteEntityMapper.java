package com.company.bsmsvc.infrastructure.persistence.mapper;

import com.company.bsmsvc.domain.model.CreditNote;
import com.company.bsmsvc.infrastructure.persistence.entity.CreditNoteEntity;
import org.springframework.stereotype.Component;

@Component
public class CreditNoteEntityMapper {
    public CreditNote toDomain(CreditNoteEntity e) {
        if (e == null) return null;
        return CreditNote.builder()
            .id(e.getId())
            .tenantId(e.getTenantId())
            .invoiceId(e.getInvoiceId())
            .creditNumber(e.getCreditNumber())
            .amountMinor(e.getAmountMinor())
            .currency(e.getCurrency())
            .reason(e.getReason())
            .status(e.getStatus())
            .createdBy(e.getCreatedBy())
            .createdAt(e.getCreatedAt())
            .updatedAt(e.getUpdatedAt())
            .version(e.getVersion())
            .build();
    }

    public CreditNoteEntity toEntity(CreditNote d) {
        if (d == null) return null;
        return CreditNoteEntity.builder()
            .id(d.getId())
            .tenantId(d.getTenantId())
            .invoiceId(d.getInvoiceId())
            .creditNumber(d.getCreditNumber())
            .amountMinor(d.getAmountMinor())
            .currency(d.getCurrency())
            .reason(d.getReason())
            .status(d.getStatus())
            .createdBy(d.getCreatedBy())
            .createdAt(d.getCreatedAt())
            .updatedAt(d.getUpdatedAt())
            .version(d.getVersion())
            .build();
    }
}
