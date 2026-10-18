package com.company.bsmsvc.api.mapper;

import com.company.bsmsvc.api.dto.response.PaymentMethodResponse;
import com.company.bsmsvc.api.dto.response.PaymentMethodSummaryResponse;
import com.company.bsmsvc.domain.model.PaymentMethod;
import org.springframework.stereotype.Component;

@Component
public class PaymentMethodApiMapper {

    public PaymentMethodResponse toResponse(PaymentMethod pm) {
        return new PaymentMethodResponse(
            pm.getId(), pm.getTenantId(), pm.getPaymentProvider(),
            pm.getExternalPaymentMethodId(), pm.getType(), pm.getBrand(),
            pm.getLastFour(), pm.getExpMonth(), pm.getExpYear(),
            pm.isDefault(), pm.getStatus(), pm.getCreatedAt(), pm.getUpdatedAt()
        );
    }

    public PaymentMethodSummaryResponse toSummary(PaymentMethod pm) {
        return new PaymentMethodSummaryResponse(
            pm.getId(), pm.getType(), pm.getBrand(),
            pm.getLastFour(), pm.getExpMonth(), pm.getExpYear(),
            pm.isDefault(), pm.getStatus()
        );
    }
}
