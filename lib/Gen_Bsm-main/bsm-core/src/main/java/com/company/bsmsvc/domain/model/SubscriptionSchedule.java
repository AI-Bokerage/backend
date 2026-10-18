package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.SubscriptionScheduleActionType;
import com.company.bsmsvc.domain.enums.SubscriptionScheduleStatus;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionSchedule {

    private UUID id;
    private UUID subscriptionId;
    private UUID tenantId;
    private SubscriptionScheduleActionType actionType;
    private UUID targetPlanVersionId;
    private Instant effectiveAt;
    private SubscriptionScheduleStatus status;
    private UUID createdBy;
    private Instant executedAt;
    private String executedBy;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public void cancel() {
        if (status != SubscriptionScheduleStatus.PENDING) {
            throw new BusinessRuleViolationException("Only pending schedules can be cancelled");
        }
        this.status = SubscriptionScheduleStatus.CANCELLED;
    }
}
