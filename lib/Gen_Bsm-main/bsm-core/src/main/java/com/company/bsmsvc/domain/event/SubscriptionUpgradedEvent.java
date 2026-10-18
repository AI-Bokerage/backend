package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionUpgradedEvent(
    UUID subscriptionId,
    UUID tenantId,
    UUID fromPlanVersionId,
    UUID toPlanVersionId,
    Instant occurredAt
) {
}
