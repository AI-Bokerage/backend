package com.company.bsmsvc.api.dto.response;

import com.company.bsmsvc.domain.enums.DunningAttemptStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Single dunning attempt record")
public record DunningAttemptResponse(
    UUID id,
    UUID subscriptionId,
    UUID invoiceId,
    int attemptNumber,
    DunningAttemptStatus status,
    String failureCode,
    String failureMessage,
    String externalPaymentId,
    Instant nextRetryAt,
    Instant attemptedAt,
    Instant createdAt
) {}
