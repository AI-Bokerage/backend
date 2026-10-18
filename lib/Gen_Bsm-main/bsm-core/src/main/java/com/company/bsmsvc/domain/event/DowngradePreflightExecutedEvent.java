package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;

public record DowngradePreflightExecutedEvent(
    UUID subscriptionId,
    UUID tenantId,
    UUID targetPlanVersionId,
    boolean anyOverLimit,
    Instant occurredAt
) {
}
