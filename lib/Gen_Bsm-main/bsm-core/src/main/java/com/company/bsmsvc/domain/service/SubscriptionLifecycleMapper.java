package com.company.bsmsvc.domain.service;

import com.company.bsmsvc.domain.enums.ActorType;
import com.company.bsmsvc.domain.enums.SubscriptionEventType;
import com.company.bsmsvc.domain.enums.SubscriptionHistoryAction;
import com.company.bsmsvc.domain.enums.SubscriptionScheduleActionType;
import com.company.bsmsvc.domain.enums.SubscriptionScheduleStatus;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.SubscriptionEvent;
import com.company.bsmsvc.domain.model.SubscriptionHistory;
import com.company.bsmsvc.domain.model.SubscriptionSchedule;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionLifecycleMapper {

    public Subscription toNewSubscription(
        Subscription draft,
        SubscriptionStatus initialStatus,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant trialEndsAt,
        Instant now
    ) {
        return draft.toBuilder()
            .id(UUID.randomUUID())
            .status(initialStatus)
            .currentPeriodStart(currentPeriodStart)
            .currentPeriodEnd(currentPeriodEnd)
            .trialEndsAt(trialEndsAt)
            .cancelAtPeriodEnd(false)
            .version(null)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    public SubscriptionSchedule toCancellationSchedule(Subscription subscription, Instant effectiveAt, UUID createdBy, Instant now) {
        return SubscriptionSchedule.builder()
            .id(UUID.randomUUID())
            .subscriptionId(subscription.getId())
            .tenantId(subscription.getTenantId())
            .actionType(SubscriptionScheduleActionType.CANCEL_SUBSCRIPTION)
            .targetPlanVersionId(null)
            .effectiveAt(effectiveAt)
            .status(SubscriptionScheduleStatus.PENDING)
            .createdBy(createdBy)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    public SubscriptionSchedule toDowngradeSchedule(
        Subscription subscription,
        UUID targetPlanVersionId,
        Instant effectiveAt,
        UUID createdBy,
        Instant now
    ) {
        return SubscriptionSchedule.builder()
            .id(UUID.randomUUID())
            .subscriptionId(subscription.getId())
            .tenantId(subscription.getTenantId())
            .actionType(SubscriptionScheduleActionType.DOWNGRADE_SUBSCRIPTION)
            .targetPlanVersionId(targetPlanVersionId)
            .effectiveAt(effectiveAt)
            .status(SubscriptionScheduleStatus.PENDING)
            .createdBy(createdBy)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    public SubscriptionSchedule withUpdatedAt(SubscriptionSchedule schedule, Instant updatedAt) {
        return schedule.toBuilder().updatedAt(updatedAt).build();
    }

    public SubscriptionHistory toHistory(
        Subscription subscription,
        SubscriptionHistoryAction action,
        UUID fromPlanVersionId,
        UUID toPlanVersionId,
        String reason,
        String performedBy,
        UUID actorId,
        ActorType actorType,
        Instant occurredAt
    ) {
        return SubscriptionHistory.builder()
            .id(UUID.randomUUID())
            .subscriptionId(subscription.getId())
            .tenantId(subscription.getTenantId())
            .action(action)
            .fromPlanVersionId(fromPlanVersionId)
            .toPlanVersionId(toPlanVersionId)
            .reason(reason)
            .performedBy(performedBy)
            .actorId(actorId)
            .actorType(actorType)
            .occurredAt(occurredAt)
            .build();
    }

    public SubscriptionEvent toEvent(
        Subscription subscription,
        SubscriptionEventType eventType,
        Map<String, Object> payload,
        UUID actorId,
        ActorType actorType,
        Instant occurredAt
    ) {
        return SubscriptionEvent.builder()
            .id(UUID.randomUUID())
            .subscriptionId(subscription.getId())
            .tenantId(subscription.getTenantId())
            .eventType(eventType)
            .payload(payload)
            .eventVersion(1)
            .actorId(actorId)
            .actorType(actorType)
            .occurredAt(occurredAt)
            .createdAt(occurredAt)
            .build();
    }
}