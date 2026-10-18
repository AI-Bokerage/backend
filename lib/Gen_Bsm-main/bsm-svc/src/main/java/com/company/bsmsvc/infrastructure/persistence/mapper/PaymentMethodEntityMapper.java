package com.company.bsmsvc.infrastructure.persistence.mapper;

import com.company.bsmsvc.domain.model.PaymentMethod;
import com.company.bsmsvc.infrastructure.persistence.entity.PaymentMethodEntity;
import org.springframework.stereotype.Component;

@Component
public class PaymentMethodEntityMapper {

    public PaymentMethod toDomain(PaymentMethodEntity e) {
        if (e == null) return null;
        return PaymentMethod.builder()
            .id(e.getId())
            .tenantId(e.getTenantId())
            .paymentProvider(e.getPaymentProvider())
            .externalPaymentMethodId(e.getExternalPaymentMethodId())
            .type(e.getType())
            .brand(e.getBrand())
            .lastFour(e.getLastFour())
            .expMonth(e.getExpMonth())
            .expYear(e.getExpYear())
            .isDefault(e.isDefault())
            .status(e.getStatus())
            .createdAt(e.getCreatedAt())
            .updatedAt(e.getUpdatedAt())
            .version(e.getVersion())
            .build();
    }

    public PaymentMethodEntity toEntity(PaymentMethod d) {
        if (d == null) return null;
        return PaymentMethodEntity.builder()
            .id(d.getId())
            .tenantId(d.getTenantId())
            .paymentProvider(d.getPaymentProvider())
            .externalPaymentMethodId(d.getExternalPaymentMethodId())
            .type(d.getType())
            .brand(d.getBrand())
            .lastFour(d.getLastFour())
            .expMonth(d.getExpMonth())
            .expYear(d.getExpYear())
            .isDefault(d.isDefault())
            .status(d.getStatus())
            .createdAt(d.getCreatedAt())
            .updatedAt(d.getUpdatedAt())
            .version(d.getVersion())
            .build();
    }
}
