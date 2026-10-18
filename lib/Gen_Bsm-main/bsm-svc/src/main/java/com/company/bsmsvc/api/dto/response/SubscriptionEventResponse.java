package com.company.bsmsvc.api.dto.response;

import com.company.bsmsvc.domain.enums.ActorType;
import com.company.bsmsvc.domain.enums.SubscriptionEventType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SubscriptionEventResponse(
    UUID id,
    UUID subscriptionId,
    UUID tenantId,
    SubscriptionEventType eventType,
    Map<String, Object> payload,
    int eventVersion,
    UUID actorId,
    ActorType actorType,
    Instant occurredAt,
    Instant createdAt
) {
}
