package com.company.bsmsvc.infrastructure.persistence.mapper;

import com.company.bsmsvc.domain.model.DunningAttempt;
import com.company.bsmsvc.infrastructure.persistence.entity.DunningAttemptEntity;
import org.springframework.stereotype.Component;

@Component
public class DunningAttemptEntityMapper {

    public DunningAttempt toDomain(DunningAttemptEntity e) {
        if (e == null) return null;
        return DunningAttempt.builder()
            .id(e.getId()).version(e.getVersion()).subscriptionId(e.getSubscriptionId()).tenantId(e.getTenantId())
            .invoiceId(e.getInvoiceId()).attemptNumber(e.getAttemptNumber()).status(e.getStatus())
            .failureCode(e.getFailureCode()).failureMessage(e.getFailureMessage())
            .externalPaymentId(e.getExternalPaymentId()).nextRetryAt(e.getNextRetryAt())
            .attemptedAt(e.getAttemptedAt()).createdAt(e.getCreatedAt()).build();
    }

    public DunningAttemptEntity toEntity(DunningAttempt d) {
        if (d == null) return null;
        return DunningAttemptEntity.builder()
            .id(d.getId()).version(d.getVersion()).subscriptionId(d.getSubscriptionId()).tenantId(d.getTenantId())
            .invoiceId(d.getInvoiceId()).attemptNumber(d.getAttemptNumber()).status(d.getStatus())
            .failureCode(d.getFailureCode()).failureMessage(d.getFailureMessage())
            .externalPaymentId(d.getExternalPaymentId()).nextRetryAt(d.getNextRetryAt())
            .attemptedAt(d.getAttemptedAt()).createdAt(d.getCreatedAt() == null ? java.time.Instant.now() : d.getCreatedAt()).build();
    }
}
