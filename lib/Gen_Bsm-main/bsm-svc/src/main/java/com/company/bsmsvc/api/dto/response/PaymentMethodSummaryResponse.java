package com.company.bsmsvc.api.dto.response;

import com.company.bsmsvc.domain.enums.PaymentMethodStatus;
import com.company.bsmsvc.domain.enums.PaymentMethodType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Payment method summary (for list responses)")
public record PaymentMethodSummaryResponse(
    UUID id,
    PaymentMethodType type,
    String brand,
    String lastFour,
    Integer expMonth,
    Integer expYear,
    boolean isDefault,
    PaymentMethodStatus status
) {}
