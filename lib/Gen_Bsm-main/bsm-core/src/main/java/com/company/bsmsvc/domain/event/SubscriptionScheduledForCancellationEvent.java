package com.company.bsmsvc.domain.event;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionScheduledForCancellationEvent(UUID subscriptionId, UUID tenantId, Instant effectiveAt, Instant occurredAt) {
}
