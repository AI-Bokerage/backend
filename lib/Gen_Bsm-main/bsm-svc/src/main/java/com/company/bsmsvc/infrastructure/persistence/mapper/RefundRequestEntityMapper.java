package com.company.bsmsvc.infrastructure.persistence.mapper;

import com.company.bsmsvc.domain.model.RefundRequest;
import com.company.bsmsvc.infrastructure.persistence.entity.RefundRequestEntity;
import org.springframework.stereotype.Component;

@Component
public class RefundRequestEntityMapper {

    public RefundRequest toDomain(RefundRequestEntity e) {
        if (e == null) return null;
        return RefundRequest.builder()
            .id(e.getId()).tenantId(e.getTenantId()).invoiceId(e.getInvoiceId())
            .paymentId(e.getPaymentId()).requestedAmountMinor(e.getRequestedAmountMinor())
            .provider(e.getProvider()).providerRefundId(e.getProviderRefundId())
            .status(e.getStatus()).creditNoteId(e.getCreditNoteId())
            .failureReason(e.getFailureReason()).createdAt(e.getCreatedAt())
            .updatedAt(e.getUpdatedAt()).version(e.getVersion()).build();
    }

    public RefundRequestEntity toEntity(RefundRequest d) {
        if (d == null) return null;
        return RefundRequestEntity.builder()
            .id(d.getId()).tenantId(d.getTenantId()).invoiceId(d.getInvoiceId())
            .paymentId(d.getPaymentId()).requestedAmountMinor(d.getRequestedAmountMinor())
            .provider(d.getProvider()).providerRefundId(d.getProviderRefundId())
            .status(d.getStatus()).creditNoteId(d.getCreditNoteId())
            .failureReason(d.getFailureReason()).createdAt(d.getCreatedAt())
            .updatedAt(d.getUpdatedAt())
            // null version → Spring Data uses em.persist (INSERT) instead of em.merge
            .version(d.getVersion()).build();
    }
}
