package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.domain.service.SubscriptionLifecycleMapper;
import com.company.bsmsvc.application.service.SubscriptionService;
import com.company.bsmsvc.application.service.SubscriptionSynchronizationService;
import com.company.bsmsvc.domain.enums.ActorType;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.SubscriptionEventType;
import com.company.bsmsvc.domain.enums.SubscriptionHistoryAction;
import com.company.bsmsvc.domain.enums.SubscriptionScheduleActionType;
import com.company.bsmsvc.domain.enums.SubscriptionScheduleStatus;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.event.SubscriptionCancelledEvent;
import com.company.bsmsvc.domain.event.SubscriptionCreatedEvent;
import com.company.bsmsvc.domain.event.SubscriptionDowngradeCancelledEvent;
import com.company.bsmsvc.domain.event.SubscriptionDowngradeScheduledEvent;
import com.company.bsmsvc.domain.event.SubscriptionPausedEvent;
import com.company.bsmsvc.domain.event.SubscriptionResumedEvent;
import com.company.bsmsvc.domain.event.SubscriptionScheduledForCancellationEvent;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.exception.SubscriptionNotFoundException;
import com.company.bsmsvc.domain.exception.TrialAlreadyConsumedException;
import com.company.bsmsvc.domain.port.UserUsagePort;
import com.company.bsmsvc.domain.port.TenantTrialRecordRepositoryPort;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.SubscriptionEvent;
import com.company.bsmsvc.domain.model.SubscriptionEventFilter;
import com.company.bsmsvc.domain.model.SubscriptionHistory;
import com.company.bsmsvc.domain.model.SubscriptionHistoryFilter;
import com.company.bsmsvc.domain.model.SubscriptionSchedule;
import com.company.bsmsvc.domain.model.SubscriptionScheduleFilter;
import com.company.bsmsvc.domain.port.SubscriptionEventRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionScheduleRepositoryPort;
import com.company.bsmsvc.domain.service.TenantOwnershipValidator;
import com.company.bsmsvc.domain.port.UsageLimitsSeedingPort;
import com.company.bsmsvc.domain.port.TenantScopePort;
import com.company.bsmsvc.domain.port.SubscriptionEventPublisherPort;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepositoryPort subscriptionRepositoryPort;
    private final SubscriptionHistoryRepositoryPort subscriptionHistoryRepositoryPort;
    private final SubscriptionEventRepositoryPort subscriptionEventRepositoryPort;
    private final SubscriptionScheduleRepositoryPort subscriptionScheduleRepositoryPort;
    private final SubscriptionLifecycleMapper subscriptionLifecycleMapper;
    private final TenantOwnershipValidator tenantOwnershipValidator;
    private final SubscriptionSynchronizationService subscriptionSynchronizationService;
    private final UsageLimitsSeedingPort usgLimitsSeedingService;
    private final TenantScopePort tenantScopeEnforcer;
    private final SubscriptionEventPublisherPort subscriptionEventPublisher;
    private final TenantTrialRecordRepositoryPort tenantTrialRecordRepository;
    private final UserUsagePort userUsagePort;

    @Override
    @Transactional
    public Subscription createSubscription(Subscription draft, Integer trialDays, String reason, String performedBy) {
        log.debug("[createSubscription] tenantId={}, planVersionId={}, billingCycle={}, trialDays={}",
            draft.getTenantId(), draft.getPlanVersionId(), draft.getBillingCycle(), trialDays);

        tenantScopeEnforcer.assertTenantAccess(draft.getTenantId());
        // Fix 2: validate tenant before any persistence
        if (draft.getTenantId() == null) {
            log.warn("[createSubscription] tenantId is null in draft");
            throw new BusinessRuleViolationException("tenantId must be provided for subscription creation");
        }

        subscriptionRepositoryPort.findCurrentByTenantId(draft.getTenantId())
            .ifPresent(existing -> {
                log.warn("[createSubscription] Tenant already has active subscription. tenantId={}, existingId={}",
                    draft.getTenantId(), existing.getId());
                throw new BusinessRuleViolationException("Tenant already has an active subscription");
            });

        // One trial per tenant lifetime: reject if trialDays requested but trial already consumed
        if (trialDays != null && trialDays > 0
                && tenantTrialRecordRepository.existsByTenantId(draft.getTenantId())) {
            log.warn("[createSubscription] Trial already consumed tenantId={}", draft.getTenantId());
            throw new TrialAlreadyConsumedException(draft.getTenantId());
        }

        Instant now = Instant.now();
        Instant currentPeriodStart = now;
        Instant currentPeriodEnd = calculatePeriodEnd(now, draft.getBillingCycle());
        Instant trialEndsAt = trialDays != null && trialDays > 0 ? now.plusSeconds(trialDays.longValue() * 24 * 60 * 60) : null;
        SubscriptionStatus initialStatus = trialEndsAt != null ? SubscriptionStatus.TRIALING : SubscriptionStatus.ACTIVE;

        Subscription toCreate = subscriptionLifecycleMapper.toNewSubscription(
            draft, initialStatus, currentPeriodStart, currentPeriodEnd, trialEndsAt, now
        );

        Subscription saved = subscriptionRepositoryPort.save(toCreate);

        // Write the trial record atomically with the subscription in the same transaction.
        // The PRIMARY KEY on tenant_trial_records.tenant_id provides DB-level concurrency
        // protection: a second concurrent transaction that passes the check above will fail
        // here with a unique constraint violation, rolling back the entire transaction.
        if (saved.getStatus() == SubscriptionStatus.TRIALING) {
            tenantTrialRecordRepository.markTrialConsumed(saved.getTenantId(), saved.getId(), now);
            log.info("[createSubscription] Trial record written tenantId={} subscriptionId={}",
                saved.getTenantId(), saved.getId());
        }
        UUID actorId = toActorUuid(performedBy);
        createHistory(saved, SubscriptionHistoryAction.SUBSCRIPTION_CREATED, null, saved.getPlanVersionId(), reason, performedBy, actorId, ActorType.USER, now);
        createEvent(saved, SubscriptionEventType.SUBSCRIPTION_CREATED, Map.of("reason", safe(reason), "performedBy", safe(performedBy)), actorId, ActorType.USER, now);

        log.debug("[createSubscription] subscriptionId={} domain events pending={}",  saved.getId(), saved.getDomainEvents() != null ? saved.getDomainEvents().size() : 0);
        log.info("[createSubscription] SUCCESS subscriptionId={}, tenantId={}, status={}, billingCycle={}",
            saved.getId(), saved.getTenantId(), saved.getStatus(), saved.getBillingCycle());

        // Persist outbox event within the same transaction — delivered asynchronously after commit
        subscriptionEventPublisher.publishCreated(saved);

        // Provider sync runs AFTER the BSM transaction commits so a DB rollback cannot
        // orphan a Stripe/Razorpay subscription that was already created.
        // In unit-test context isSynchronizationActive() is false, so the else-branch
        // keeps synchronous behaviour and existing unit tests continue to pass.
        final Subscription committedSub = saved;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        subscriptionSynchronizationService.syncCreate(committedSub);
                    } catch (Exception e) {
                        log.error("[createSubscription] syncCreate failed after commit for subscriptionId={}", committedSub.getId(), e);
                    }
                    // Best-effort accelerator: seed USG-SVC's quota limits synchronously
                    // instead of waiting on the async bsm.subscription.created consumer.
                    // Never throws — a failure here just falls back to that async path.
                    usgLimitsSeedingService.seedLimits(committedSub.getTenantId(), committedSub.getPpmPlanId());
                }
            });
        } else {
            subscriptionSynchronizationService.syncCreate(saved);
            usgLimitsSeedingService.seedLimits(saved.getTenantId(), saved.getPpmPlanId());
        }

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Subscription getCurrentSubscription(UUID tenantId) {
        tenantScopeEnforcer.assertTenantAccess(tenantId);
        log.debug("[getCurrentSubscription] tenantId={}", tenantId);
        Subscription result = subscriptionRepositoryPort.findCurrentByTenantId(tenantId)
            .orElseThrow(() -> {
                log.warn("[getCurrentSubscription] No active subscription found. tenantId={}", tenantId);
                return new SubscriptionNotFoundException("No active subscription found for tenant: " + tenantId);
            });
        log.debug("[getCurrentSubscription] Found subscriptionId={}, status={}", result.getId(), result.getStatus());
        return result;
    }

    @Override
    @Transactional
    public Subscription cancelSubscription(UUID tenantId, String reason, String performedBy, boolean cancelImmediately) {
        tenantScopeEnforcer.assertTenantAccess(tenantId);
        log.debug("[cancelSubscription] tenantId={}, cancelImmediately={}, performedBy={}", tenantId, cancelImmediately, performedBy);
        Subscription subscription = getCurrentSubscription(tenantId);
        log.debug("[cancelSubscription] Delegating to cancelSubscriptionById. subscriptionId={}", subscription.getId());
        return cancelSubscriptionById(subscription.getId(), tenantId, reason, performedBy, !cancelImmediately);
    }

    @Override
    @Transactional
    public Subscription pauseSubscription(UUID subscriptionId, UUID tenantId, String reason, String performedBy) {
        tenantScopeEnforcer.assertTenantAccess(tenantId);
        log.debug("[pauseSubscription] subscriptionId={}, tenantId={}, performedBy={}", subscriptionId, tenantId, performedBy);
        Subscription subscription = subscriptionRepositoryPort.findById(subscriptionId)
            .orElseThrow(() -> {
                log.warn("[pauseSubscription] Subscription not found. subscriptionId={}", subscriptionId);
                return new SubscriptionNotFoundException("Subscription not found: " + subscriptionId);
            });
        tenantOwnershipValidator.validate(subscription, tenantId);

        if (subscription.getStatus() == SubscriptionStatus.PAUSED) {
            log.warn("[pauseSubscription] IDEMPOTENT - already PAUSED. subscriptionId={}, tenantId={}", subscriptionId, tenantId);
            return subscription;
        }

        subscription.pause();
        Subscription saved = subscriptionRepositoryPort.save(subscription);
        Instant now = Instant.now();
        UUID actorId = toActorUuid(performedBy);
        createHistory(saved, SubscriptionHistoryAction.SUBSCRIPTION_PAUSED, saved.getPlanVersionId(), saved.getPlanVersionId(), reason, performedBy, actorId, ActorType.USER, now);
        createEvent(saved, SubscriptionEventType.SUBSCRIPTION_PAUSED, Map.of("reason", safe(reason), "performedBy", safe(performedBy)), actorId, ActorType.USER, now);

        SubscriptionPausedEvent domainEvent = new SubscriptionPausedEvent(saved.getId(), saved.getTenantId(), now);
        saved.registerEvent(domainEvent);
        log.debug("[pauseSubscription] subscriptionId={} domain events registered", saved.getId());
        log.info("[pauseSubscription] SUCCESS subscriptionId={}, tenantId={}, performedBy={}", saved.getId(), tenantId, performedBy);
        subscriptionEventPublisher.publishChanged(saved, null, "paused");
        return saved;
    }

    @Override
    @Transactional
    public Subscription resumeSubscription(UUID subscriptionId, UUID tenantId, String reason, String performedBy) {
        tenantScopeEnforcer.assertTenantAccess(tenantId);
        log.debug("[resumeSubscription] subscriptionId={}, tenantId={}, performedBy={}", subscriptionId, tenantId, performedBy);
        Subscription subscription = subscriptionRepositoryPort.findById(subscriptionId)
            .orElseThrow(() -> {
                log.warn("[resumeSubscription] Subscription not found. subscriptionId={}", subscriptionId);
                return new SubscriptionNotFoundException("Subscription not found: " + subscriptionId);
            });
        tenantOwnershipValidator.validate(subscription, tenantId);

        if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            log.warn("[resumeSubscription] IDEMPOTENT - already ACTIVE. subscriptionId={}, tenantId={}", subscriptionId, tenantId);
            return subscription;
        }

        subscription.resume();
        Subscription saved = subscriptionRepositoryPort.save(subscription);
        Instant now = Instant.now();
        UUID actorId = toActorUuid(performedBy);
        createHistory(saved, SubscriptionHistoryAction.SUBSCRIPTION_RESUMED, saved.getPlanVersionId(), saved.getPlanVersionId(), reason, performedBy, actorId, ActorType.USER, now);
        createEvent(saved, SubscriptionEventType.SUBSCRIPTION_RESUMED, Map.of("reason", safe(reason), "performedBy", safe(performedBy)), actorId, ActorType.USER, now);

        SubscriptionResumedEvent domainEvent = new SubscriptionResumedEvent(saved.getId(), saved.getTenantId(), now);
        saved.registerEvent(domainEvent);
        log.debug("[resumeSubscription] subscriptionId={} domain events registered", saved.getId());
        log.info("[resumeSubscription] SUCCESS subscriptionId={}, tenantId={}, performedBy={}", saved.getId(), tenantId, performedBy);
        subscriptionEventPublisher.publishChanged(saved, null, "resumed");
        // No external provider update is required for resume when only the local subscription status changes.
        return saved;
    }

    @Override
    @Transactional
    public Subscription cancelSubscriptionById(UUID subscriptionId, UUID tenantId, String reason, String performedBy, boolean cancelAtPeriodEnd) {
        tenantScopeEnforcer.assertTenantAccess(tenantId);
        log.debug("[cancelSubscriptionById] subscriptionId={}, tenantId={}, cancelAtPeriodEnd={}, performedBy={}",
            subscriptionId, tenantId, cancelAtPeriodEnd, performedBy);
        Subscription subscription = subscriptionRepositoryPort.findById(subscriptionId)
            .orElseThrow(() -> {
                log.warn("[cancelSubscriptionById] Subscription not found. subscriptionId={}", subscriptionId);
                return new SubscriptionNotFoundException("Subscription not found: " + subscriptionId);
            });
        tenantOwnershipValidator.validate(subscription, tenantId);
        Instant now = Instant.now();
        UUID actorId = toActorUuid(performedBy);

        if (cancelAtPeriodEnd) {
            if (subscription.isCancelAtPeriodEnd()) {
                log.warn("[cancelSubscriptionById] IDEMPOTENT - cancelAtPeriodEnd already set. subscriptionId={}, tenantId={}",
                    subscriptionId, tenantId);
                return subscription;
            }

            Instant effectiveAt = subscription.getCurrentPeriodEnd();
            subscription.scheduleCancellation(effectiveAt);
            Subscription saved = subscriptionRepositoryPort.save(subscription);

            // Fix 4: duplicate protection — the DB unique index (V18) guards concurrent inserts;
            // application layer idempotently re-fetches when a race is detected
            SubscriptionSchedule schedule = subscriptionScheduleRepositoryPort
                .findPendingBySubscriptionIdAndActionType(saved.getId(), SubscriptionScheduleActionType.CANCEL_SUBSCRIPTION)
                .orElseGet(() -> {
                    try {
                        return subscriptionScheduleRepositoryPort.save(
                            subscriptionLifecycleMapper.toCancellationSchedule(saved, effectiveAt, actorId, now));
                    } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                        log.info("[cancelSubscriptionById] Concurrent CANCEL schedule insert detected, re-fetching. subscriptionId={}", saved.getId());
                        return subscriptionScheduleRepositoryPort
                            .findPendingBySubscriptionIdAndActionType(saved.getId(), SubscriptionScheduleActionType.CANCEL_SUBSCRIPTION)
                            .orElseThrow(() -> ex);
                    }
                });

            createHistory(saved, SubscriptionHistoryAction.SUBSCRIPTION_SCHEDULED_FOR_CANCELLATION, saved.getPlanVersionId(), saved.getPlanVersionId(), reason, performedBy, actorId, ActorType.USER, now);
            createEvent(saved, SubscriptionEventType.SUBSCRIPTION_SCHEDULED_FOR_CANCELLATION, Map.of("reason", safe(reason), "performedBy", safe(performedBy), "scheduleId", schedule.getId().toString()), actorId, ActorType.USER, now);

            SubscriptionScheduledForCancellationEvent domainEvent = new SubscriptionScheduledForCancellationEvent(saved.getId(), saved.getTenantId(), effectiveAt, now);
            saved.registerEvent(domainEvent);
            log.debug("[cancelSubscriptionById] subscriptionId={} domain events registered", saved.getId());
            log.info("[cancelSubscriptionById] SCHEDULED subscriptionId={}, tenantId={}, effectiveAt={}, scheduleId={}",
                saved.getId(), tenantId, effectiveAt, schedule.getId());
            return saved;
        }

        if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            log.warn("[cancelSubscriptionById] IDEMPOTENT - already CANCELLED. subscriptionId={}, tenantId={}", subscriptionId, tenantId);
            return subscription;
        }

        subscription.cancel(now);
        Subscription updated = subscriptionRepositoryPort.save(subscription);
        createHistory(updated, SubscriptionHistoryAction.SUBSCRIPTION_CANCELLED, updated.getPlanVersionId(), updated.getPlanVersionId(), reason, performedBy, actorId, ActorType.USER, now);
        createEvent(updated, SubscriptionEventType.SUBSCRIPTION_CANCELLED, Map.of("reason", safe(reason), "performedBy", safe(performedBy)), actorId, ActorType.USER, now);

        SubscriptionCancelledEvent domainEvent = new SubscriptionCancelledEvent(updated.getId(), updated.getTenantId(), now);
        updated.registerEvent(domainEvent);
        log.debug("[cancelSubscriptionById] subscriptionId={} domain events registered", updated.getId());
        log.info("[cancelSubscriptionById] CANCELLED IMMEDIATELY subscriptionId={}, tenantId={}, performedBy={}",
            updated.getId(), tenantId, performedBy);
        subscriptionEventPublisher.publishCanceled(updated);
        subscriptionSynchronizationService.syncCancel(updated, true);
        return updated;
    }

    @Override
    @Transactional
    public SubscriptionSchedule scheduleDowngrade(UUID subscriptionId, UUID tenantId, UUID targetPlanVersionId, Instant effectiveAt, String reason, UUID createdBy) {
        tenantScopeEnforcer.assertTenantAccess(tenantId);
        log.debug("[scheduleDowngrade] subscriptionId={}, tenantId={}, targetPlanVersionId={}, effectiveAt={}, createdBy={}",
            subscriptionId, tenantId, targetPlanVersionId, effectiveAt, createdBy);
        Subscription subscription = subscriptionRepositoryPort.findById(subscriptionId)
            .orElseThrow(() -> {
                log.warn("[scheduleDowngrade] Subscription not found. subscriptionId={}", subscriptionId);
                return new SubscriptionNotFoundException("Subscription not found: " + subscriptionId);
            });
        tenantOwnershipValidator.validate(subscription, tenantId);

        if (subscription.getPpmPlanVersionId() == null) {
            throw new BusinessRuleViolationException("Downgrade scheduling is only supported for PPM-backed subscriptions");
        }

        if (targetPlanVersionId.equals(subscription.getPpmPlanVersionId())) {
            throw new BusinessRuleViolationException("Cannot downgrade to the same plan version");
        }

        if (!effectiveAt.isAfter(Instant.now())) {
            log.warn("[scheduleDowngrade] effectiveAt is not in the future. subscriptionId={}, effectiveAt={}", subscriptionId, effectiveAt);
            throw new BusinessRuleViolationException("Downgrade effectiveAt must be in the future");
        }

        SubscriptionSchedule existing = subscriptionScheduleRepositoryPort
            .findPendingBySubscriptionIdAndActionType(subscriptionId, SubscriptionScheduleActionType.DOWNGRADE_SUBSCRIPTION)
            .orElse(null);
        if (existing != null && existing.getTargetPlanVersionId().equals(targetPlanVersionId) && existing.getEffectiveAt().equals(effectiveAt)) {
            log.warn("[scheduleDowngrade] IDEMPOTENT - identical pending downgrade exists. subscriptionId={}, scheduleId={}",
                subscriptionId, existing.getId());
            return existing;
        }
        if (existing != null) {
            log.warn("[scheduleDowngrade] Conflict - different pending downgrade exists. subscriptionId={}, existingScheduleId={}",
                subscriptionId, existing.getId());
            throw new BusinessRuleViolationException("A pending downgrade already exists for this subscription");
        }

        Instant now = Instant.now();
        // Fix 4: duplicate protection — wrap save with concurrent-insert handling
        SubscriptionSchedule schedule;
        try {
            schedule = subscriptionScheduleRepositoryPort.save(
                subscriptionLifecycleMapper.toDowngradeSchedule(subscription, targetPlanVersionId, effectiveAt, createdBy, now));
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            log.info("[scheduleDowngrade] Concurrent DOWNGRADE schedule insert detected, re-fetching. subscriptionId={}", subscriptionId);
            schedule = subscriptionScheduleRepositoryPort
                .findPendingBySubscriptionIdAndActionType(subscriptionId, SubscriptionScheduleActionType.DOWNGRADE_SUBSCRIPTION)
                .orElseThrow(() -> ex);
        }

        createHistory(subscription, SubscriptionHistoryAction.SUBSCRIPTION_DOWNGRADE_SCHEDULED, subscription.getPlanVersionId(), targetPlanVersionId, reason, createdBy.toString(), createdBy, ActorType.USER, now);
        createEvent(subscription, SubscriptionEventType.SUBSCRIPTION_DOWNGRADE_SCHEDULED, Map.of("reason", safe(reason), "createdBy", createdBy.toString(), "targetPlanVersionId", targetPlanVersionId.toString()), createdBy, ActorType.USER, now);

        SubscriptionDowngradeScheduledEvent domainEvent = new SubscriptionDowngradeScheduledEvent(subscriptionId, subscription.getTenantId(), targetPlanVersionId, effectiveAt, now);
        subscription.registerEvent(domainEvent); // Fix 1: register event on aggregate
        log.debug("[scheduleDowngrade] subscriptionId={} domain events registered", subscriptionId);
        log.info("[scheduleDowngrade] SUCCESS subscriptionId={}, tenantId={}, scheduleId={}, targetPlanVersionId={}, effectiveAt={}",
            subscriptionId, tenantId, schedule.getId(), targetPlanVersionId, effectiveAt);
        return schedule;
    }

    @Override
    @Transactional
    public SubscriptionSchedule cancelDowngrade(UUID subscriptionId, UUID tenantId, String reason, String performedBy) {
        tenantScopeEnforcer.assertTenantAccess(tenantId);
        log.debug("[cancelDowngrade] subscriptionId={}, tenantId={}, performedBy={}", subscriptionId, tenantId, performedBy);
        Subscription subscription = subscriptionRepositoryPort.findById(subscriptionId)
            .orElseThrow(() -> {
                log.warn("[cancelDowngrade] Subscription not found. subscriptionId={}", subscriptionId);
                return new SubscriptionNotFoundException("Subscription not found: " + subscriptionId);
            });
        tenantOwnershipValidator.validate(subscription, tenantId);

        SubscriptionSchedule existing = subscriptionScheduleRepositoryPort
            .findPendingBySubscriptionIdAndActionType(subscriptionId, SubscriptionScheduleActionType.DOWNGRADE_SUBSCRIPTION)
            .orElseThrow(() -> {
                log.warn("[cancelDowngrade] No pending downgrade found. subscriptionId={}", subscriptionId);
                return new BusinessRuleViolationException("No pending downgrade schedule found for subscription: " + subscriptionId);
            });

        existing.cancel();
        SubscriptionSchedule cancelled = subscriptionScheduleRepositoryPort.save(subscriptionLifecycleMapper.withUpdatedAt(existing, Instant.now()));
        Instant now = Instant.now();
        UUID actorId = toActorUuid(performedBy);

        createHistory(subscription, SubscriptionHistoryAction.SUBSCRIPTION_DOWNGRADE_CANCELLED, subscription.getPlanVersionId(), existing.getTargetPlanVersionId(), reason, performedBy, actorId, ActorType.USER, now);
        createEvent(subscription, SubscriptionEventType.SUBSCRIPTION_DOWNGRADE_CANCELLED, Map.of("reason", safe(reason), "performedBy", safe(performedBy), "targetPlanVersionId", String.valueOf(existing.getTargetPlanVersionId())), actorId, ActorType.USER, now);

        SubscriptionDowngradeCancelledEvent domainEvent = new SubscriptionDowngradeCancelledEvent(subscriptionId, subscription.getTenantId(), now);
        subscription.registerEvent(domainEvent); // Fix 1: register event on aggregate
        log.debug("[cancelDowngrade] subscriptionId={} domain events registered", subscriptionId);
        log.info("[cancelDowngrade] SUCCESS subscriptionId={}, tenantId={}, scheduleId={}, performedBy={}",
            subscriptionId, tenantId, existing.getId(), performedBy);
        return cancelled;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<SubscriptionEvent> getSubscriptionEvents(SubscriptionEventFilter filter, int page, int size, String sortBy, String sortDirection) {
        SubscriptionEventFilter effectiveFilter = new SubscriptionEventFilter(
            tenantScopeEnforcer.resolveEffectiveTenantId(filter.tenantId()),
            filter.subscriptionId(), filter.eventType(), filter.dateFrom(), filter.dateTo()
        );
        log.debug("[getSubscriptionEvents] filter={}, page={}, size={}, sortBy={}, sortDirection={}", effectiveFilter, page, size, sortBy, sortDirection);
        PageResult<SubscriptionEvent> result = subscriptionEventRepositoryPort.findEvents(effectiveFilter, page, size, sortBy, sortDirection);
        log.debug("[getSubscriptionEvents] Returning {} of {} events", result.content().size(), result.totalElements());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<SubscriptionHistory> getSubscriptionHistory(SubscriptionHistoryFilter filter, int page, int size, String sortBy, String sortDirection) {
        SubscriptionHistoryFilter effectiveFilter = new SubscriptionHistoryFilter(
            tenantScopeEnforcer.resolveEffectiveTenantId(filter.tenantId()),
            filter.subscriptionId(), filter.action(), filter.dateFrom(), filter.dateTo()
        );
        log.debug("[getSubscriptionHistory] filter={}, page={}, size={}, sortBy={}, sortDirection={}", effectiveFilter, page, size, sortBy, sortDirection);
        PageResult<SubscriptionHistory> result = subscriptionHistoryRepositoryPort.findHistory(effectiveFilter, page, size, sortBy, sortDirection);
        log.debug("[getSubscriptionHistory] Returning {} of {} records", result.content().size(), result.totalElements());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<SubscriptionSchedule> getSubscriptionSchedules(SubscriptionScheduleFilter filter, int page, int size, String sortBy, String sortDirection) {
        SubscriptionScheduleFilter effectiveFilter = new SubscriptionScheduleFilter(
            tenantScopeEnforcer.resolveEffectiveTenantId(filter.tenantId()),
            filter.subscriptionId(), filter.status(), filter.actionType()
        );
        log.debug("[getSubscriptionSchedules] filter={}, page={}, size={}, sortBy={}, sortDirection={}", effectiveFilter, page, size, sortBy, sortDirection);
        PageResult<SubscriptionSchedule> result = subscriptionScheduleRepositoryPort.findSchedules(effectiveFilter, page, size, sortBy, sortDirection);
        log.debug("[getSubscriptionSchedules] Returning {} of {} schedules", result.content().size(), result.totalElements());
        return result;
    }

    private void createHistory(
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
        subscriptionHistoryRepositoryPort.save(
            subscriptionLifecycleMapper.toHistory(subscription, action, fromPlanVersionId, toPlanVersionId, reason, performedBy, actorId, actorType, occurredAt)
        );
    }

    private void createEvent(Subscription subscription, SubscriptionEventType eventType, Map<String, Object> payload, UUID actorId, ActorType actorType, Instant occurredAt) {
        subscriptionEventRepositoryPort.save(subscriptionLifecycleMapper.toEvent(subscription, eventType, payload, actorId, actorType, occurredAt));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private UUID toActorUuid(String actor) {
        try {
            return UUID.fromString(actor);
        } catch (Exception ex) {
            return UUID.nameUUIDFromBytes(safe(actor).getBytes(StandardCharsets.UTF_8));
        }
    }

    private Instant calculatePeriodEnd(Instant start, BillingCycle billingCycle) {
        ZonedDateTime startDateTime = ZonedDateTime.ofInstant(start, ZoneOffset.UTC);
        return switch (billingCycle) {
            case MONTHLY -> startDateTime.plusMonths(1).toInstant();
            case YEARLY -> startDateTime.plusYears(1).toInstant();
        };
    }
}
