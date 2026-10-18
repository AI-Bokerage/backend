package com.company.bsmsvc.infrastructure.persistence.mapper;

import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.infrastructure.persistence.entity.TenantBillingProfileEntity;
import org.springframework.stereotype.Component;

@Component
public class TenantBillingProfileEntityMapper {

    public TenantBillingProfile toDomain(TenantBillingProfileEntity e) {
        if (e == null) return null;
        return TenantBillingProfile.builder()
            .id(e.getId())
            .tenantId(e.getTenantId())
            .paymentProvider(e.getPaymentProvider())
            .externalCustomerId(e.getExternalCustomerId())
            .currency(e.getCurrency())
            .createdAt(e.getCreatedAt())
            .updatedAt(e.getUpdatedAt())
            .version(e.getVersion())
            .build();
    }

    public TenantBillingProfileEntity toEntity(TenantBillingProfile d) {
        if (d == null) return null;
        return TenantBillingProfileEntity.builder()
            .id(d.getId())
            .tenantId(d.getTenantId())
            .paymentProvider(d.getPaymentProvider())
            .externalCustomerId(d.getExternalCustomerId())
            .currency(d.getCurrency())
            .createdAt(d.getCreatedAt())
            .updatedAt(d.getUpdatedAt())
            .version(d.getVersion())
            .build();
    }
}
