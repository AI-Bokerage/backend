package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;

public record MigrationPlanCreatedEvent(
    UUID migrationPlanId,
    UUID subscriptionId,
    UUID tenantId,
    UUID targetPlanVersionId,
    int itemCount,
    Instant occurredAt
) {
}
