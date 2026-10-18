package com.company.bsmsvc.api.dto.response;

import com.company.bsmsvc.domain.enums.PaymentMethodStatus;
import com.company.bsmsvc.domain.enums.PaymentMethodType;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Full payment method response")
public record PaymentMethodResponse(
    UUID id,
    UUID tenantId,
    PaymentProvider paymentProvider,
    String externalPaymentMethodId,
    PaymentMethodType type,
    String brand,
    String lastFour,
    Integer expMonth,
    Integer expYear,
    boolean isDefault,
    PaymentMethodStatus status,
    Instant createdAt,
    Instant updatedAt
) {}
