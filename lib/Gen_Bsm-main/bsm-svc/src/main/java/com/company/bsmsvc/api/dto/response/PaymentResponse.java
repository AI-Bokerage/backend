package com.company.bsmsvc.api.dto.response;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Payment record response")
public record PaymentResponse(
    UUID id,
    UUID tenantId,
    UUID invoiceId,
    PaymentProvider paymentProvider,
    String externalPaymentId,
    PaymentStatus status,
    long amountMinor,
    String currency,
    String failureReason,
    Instant createdAt,
    Instant updatedAt
) {}
