package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.SubscriptionScheduleActionType;
import com.company.bsmsvc.domain.enums.SubscriptionScheduleStatus;
import java.util.UUID;

public record SubscriptionScheduleFilter(
    UUID tenantId,
    UUID subscriptionId,
    SubscriptionScheduleStatus status,
    SubscriptionScheduleActionType actionType
) {
}
