package com.company.bsmsvc.application.service;

import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.SubscriptionEvent;
import com.company.bsmsvc.domain.model.SubscriptionEventFilter;
import com.company.bsmsvc.domain.model.SubscriptionHistory;
import com.company.bsmsvc.domain.model.SubscriptionHistoryFilter;
import com.company.bsmsvc.domain.model.SubscriptionSchedule;
import com.company.bsmsvc.domain.model.SubscriptionScheduleFilter;
import java.time.Instant;
import java.util.UUID;

/**
 * Manages the lifecycle of a tenant's subscription: creation, cancellation, pause/resume,
 * and scheduled downgrades.
 */
public interface SubscriptionService {

    /**
     * Creates the tenant's subscription from a draft.
     *
     * @implSpec Does <b>not</b> require a {@code TenantBillingProfile} to already exist —
     * this method only persists the {@link Subscription} row itself; a missing billing
     * profile will instead surface later, when {@link InvoiceService}/{@link PaymentService}
     * try to bill the tenant. Preconditions enforced here: {@code draft.tenantId} must be
     * non-null, and the tenant must not already have an active subscription (checked via
     * {@code findCurrentByTenantId}), else
     * {@link com.company.bsmsvc.domain.exception.BusinessRuleViolationException} is thrown.
     * <p>{@code trialDays}: {@code null} or {@code <= 0} creates the subscription immediately
     * {@code ACTIVE} with no trial. A positive value creates it {@code TRIALING} with
     * {@code trialEndsAt = now + trialDays}; in that case the tenant must not have already
     * consumed a trial (one trial per tenant lifetime, enforced via a unique
     * {@code tenant_trial_records} row), else
     * {@link com.company.bsmsvc.domain.exception.TrialAlreadyConsumedException} is thrown.
     * <p>Postconditions: on success, publishes {@code SUBSCRIPTION_CREATED} history/event
     * entries, enqueues an outbox event, and — after the transaction commits — synchronizes
     * the subscription to the payment provider and seeds usage limits. A provider-sync
     * failure after commit is logged but does not roll back or fail this call.
     */
    Subscription createSubscription(Subscription draft, Integer trialDays, String reason, String performedBy);

    /**
     * Returns the tenant's current (active/trialing/paused/etc.) subscription.
     *
     * @implSpec Throws {@link com.company.bsmsvc.domain.exception.SubscriptionNotFoundException}
     * if the tenant has none.
     */
    Subscription getCurrentSubscription(UUID tenantId);

    /**
     * Cancels the tenant's current subscription, looked up via {@link #getCurrentSubscription}.
     *
     * @implSpec Delegates to {@link #cancelSubscriptionById}; {@code cancelImmediately} is
     * inverted into that method's {@code cancelAtPeriodEnd} flag.
     */
    Subscription cancelSubscription(UUID tenantId, String reason, String performedBy, boolean cancelImmediately);

    /**
     * Pauses an {@code ACTIVE} subscription.
     *
     * @implSpec Idempotent: if the subscription is already {@code PAUSED}, returns it unchanged.
     * Requires the subscription to belong to {@code tenantId} (validated via ownership check),
     * else throws.
     */
    Subscription pauseSubscription(UUID subscriptionId, UUID tenantId, String reason, String performedBy);

    /**
     * Resumes a {@code PAUSED} subscription back to {@code ACTIVE}.
     *
     * @implSpec Idempotent: if the subscription is already {@code ACTIVE}, returns it unchanged.
     * No provider-side call is made — only the local status changes.
     */
    Subscription resumeSubscription(UUID subscriptionId, UUID tenantId, String reason, String performedBy);

    /**
     * Cancels a subscription either immediately or at period end.
     *
     * @implSpec When {@code cancelAtPeriodEnd} is true: idempotent if already scheduled for
     * cancellation, otherwise creates (or re-fetches, under a concurrent-insert race) a
     * {@code CANCEL_SUBSCRIPTION} {@link SubscriptionSchedule} effective at the current period
     * end. When false: idempotent if already {@code CANCELLED}, otherwise cancels immediately
     * and synchronizes the cancellation to the payment provider synchronously.
     */
    Subscription cancelSubscriptionById(UUID subscriptionId, UUID tenantId, String reason, String performedBy, boolean cancelAtPeriodEnd);

    /**
     * Schedules a downgrade to {@code targetPlanVersionId}, effective at a future instant.
     *
     * @implSpec Only supported for PPM-backed subscriptions ({@code ppmPlanVersionId != null}),
     * else throws {@link com.company.bsmsvc.domain.exception.BusinessRuleViolationException}.
     * {@code effectiveAt} must be strictly in the future. Idempotent for an identical pending
     * downgrade (same target + effective time); throws if a different pending downgrade already
     * exists for the subscription — call {@link #cancelDowngrade} first to replace it.
     */
    SubscriptionSchedule scheduleDowngrade(UUID subscriptionId, UUID tenantId, UUID targetPlanVersionId, Instant effectiveAt, String reason, UUID createdBy);

    /**
     * Cancels a subscription's pending scheduled downgrade.
     *
     * @implSpec Requires an existing pending {@code DOWNGRADE_SUBSCRIPTION} schedule for the
     * subscription, else throws {@link com.company.bsmsvc.domain.exception.BusinessRuleViolationException}.
     */
    SubscriptionSchedule cancelDowngrade(UUID subscriptionId, UUID tenantId, String reason, String performedBy);

    /** Paged, tenant-scoped read of subscription domain events. */
    PageResult<SubscriptionEvent> getSubscriptionEvents(SubscriptionEventFilter filter, int page, int size, String sortBy, String sortDirection);

    /** Paged, tenant-scoped read of subscription audit history entries. */
    PageResult<SubscriptionHistory> getSubscriptionHistory(SubscriptionHistoryFilter filter, int page, int size, String sortBy, String sortDirection);

    /** Paged, tenant-scoped read of subscription schedules (pending/executed downgrades and cancellations). */
    PageResult<SubscriptionSchedule> getSubscriptionSchedules(SubscriptionScheduleFilter filter, int page, int size, String sortBy, String sortDirection);
}
