package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionDowngradeScheduledEvent(
    UUID subscriptionId,
    UUID tenantId,
    UUID targetPlanVersionId,
    Instant effectiveAt,
    Instant occurredAt
) {
}
