package com.company.bsmsvc.infrastructure.persistence.mapper;

import com.company.bsmsvc.domain.model.Payment;
import com.company.bsmsvc.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.stereotype.Component;

@Component
public class PaymentEntityMapper {

    public Payment toDomain(PaymentEntity e) {
        if (e == null) return null;
        return Payment.builder()
            .id(e.getId()).tenantId(e.getTenantId()).invoiceId(e.getInvoiceId())
            .paymentProvider(e.getPaymentProvider()).externalPaymentId(e.getExternalPaymentId())
            .externalChargeId(e.getExternalChargeId()).status(e.getStatus())
            .amountMinor(e.getAmountMinor()).currency(e.getCurrency())
            .failureReason(e.getFailureReason()).createdAt(e.getCreatedAt())
            .updatedAt(e.getUpdatedAt()).version(e.getVersion()).build();
    }

    public PaymentEntity toEntity(Payment d) {
        if (d == null) return null;
        return PaymentEntity.builder()
            .id(d.getId()).tenantId(d.getTenantId()).invoiceId(d.getInvoiceId())
            .paymentProvider(d.getPaymentProvider()).externalPaymentId(d.getExternalPaymentId())
            .externalChargeId(d.getExternalChargeId()).status(d.getStatus())
            .amountMinor(d.getAmountMinor()).currency(d.getCurrency())
            .failureReason(d.getFailureReason()).createdAt(d.getCreatedAt())
            .updatedAt(d.getUpdatedAt())
            // Pass null for brand-new payments so Spring Data uses em.persist() (INSERT)
            // rather than em.merge() (SELECT then UPDATE), which avoids false optimistic-lock conflicts.
            // JPA sets the initial @Version value to 0 automatically on first persist.
            .version(d.getVersion()).build();
    }
}
