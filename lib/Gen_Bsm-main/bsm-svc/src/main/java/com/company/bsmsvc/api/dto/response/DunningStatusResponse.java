package com.company.bsmsvc.api.dto.response;

import com.company.bsmsvc.domain.enums.DunningStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Current dunning state for a subscription")
public record DunningStatusResponse(
    UUID subscriptionId,
    UUID tenantId,
    DunningStatus dunningStatus,
    Instant dunningStartedAt,
    Instant dunningNextActionAt,
    int totalAttempts,
    List<DunningAttemptResponse> attempts
) {}
