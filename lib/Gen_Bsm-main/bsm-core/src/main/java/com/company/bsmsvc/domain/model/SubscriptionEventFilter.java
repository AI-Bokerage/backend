package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.SubscriptionEventType;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionEventFilter(
    UUID tenantId,
    UUID subscriptionId,
    SubscriptionEventType eventType,
    Instant dateFrom,
    Instant dateTo
) {
}
