package com.company.bsmsvc.api.dto.response;

import com.company.bsmsvc.domain.enums.SubscriptionScheduleActionType;
import com.company.bsmsvc.domain.enums.SubscriptionScheduleStatus;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionScheduleResponse(
    UUID id,
    UUID subscriptionId,
    UUID tenantId,
    SubscriptionScheduleActionType actionType,
    UUID targetPlanVersionId,
    Instant effectiveAt,
    SubscriptionScheduleStatus status,
    UUID createdBy,
    Instant executedAt,
    String executedBy,
    String failureReason,
    Instant createdAt,
    Instant updatedAt
) {
}
