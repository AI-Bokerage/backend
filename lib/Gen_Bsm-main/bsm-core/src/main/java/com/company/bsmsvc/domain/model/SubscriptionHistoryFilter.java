package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.SubscriptionHistoryAction;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionHistoryFilter(
    UUID tenantId,
    UUID subscriptionId,
    SubscriptionHistoryAction action,
    Instant dateFrom,
    Instant dateTo
) {
}
