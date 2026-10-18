package com.company.bsmsvc.api.dto.response;

import com.company.bsmsvc.domain.enums.ActorType;
import com.company.bsmsvc.domain.enums.SubscriptionHistoryAction;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionHistoryResponse(
    UUID id,
    UUID subscriptionId,
    UUID tenantId,
    SubscriptionHistoryAction action,
    UUID fromPlanVersionId,
    UUID toPlanVersionId,
    String reason,
    String performedBy,
    UUID actorId,
    ActorType actorType,
    Instant occurredAt
) {
}
