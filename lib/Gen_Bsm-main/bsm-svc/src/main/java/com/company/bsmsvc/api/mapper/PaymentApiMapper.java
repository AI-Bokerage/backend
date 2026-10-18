package com.company.bsmsvc.api.mapper;

import com.company.bsmsvc.api.dto.response.PaymentResponse;
import com.company.bsmsvc.domain.model.Payment;
import org.springframework.stereotype.Component;

@Component
public class PaymentApiMapper {

    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
            payment.getId(), payment.getTenantId(), payment.getInvoiceId(),
            payment.getPaymentProvider(), payment.getExternalPaymentId(),
            payment.getStatus(), payment.getAmountMinor(), payment.getCurrency(),
            payment.getFailureReason(), payment.getCreatedAt(), payment.getUpdatedAt()
        );
    }
}
