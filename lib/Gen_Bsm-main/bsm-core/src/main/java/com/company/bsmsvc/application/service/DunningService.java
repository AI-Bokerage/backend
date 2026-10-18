package com.company.bsmsvc.application.service;

import com.company.bsmsvc.domain.model.DunningAttempt;
import java.util.List;
import java.util.UUID;

/**
 * Drives the failed-payment recovery ("dunning") lifecycle for a subscription: starting it
 * on payment failure, retrying on schedule, and recovering when payment eventually succeeds.
 *
 * <p>Depends transitively on {@link PaymentMethodService} (a default payment method must be on
 * file for a retry to be attempted) and on {@link InvoiceService} (to read the amount due and
 * to apply the payment once a retry succeeds).
 */
public interface DunningService {

    /**
     * Puts a subscription into dunning after a payment failure on the given invoice.
     *
     * @implSpec Preconditions: the subscription must exist (a no-op, not an error, if not
     * found — dunning is typically triggered from async webhook/event handlers); it must not
     * already be in dunning (idempotent no-op if so); and its status must not be
     * {@code PAUSED}, {@code CANCELLED}, or {@code SUSPENDED_PENDING_PURGE} (silently skipped
     * otherwise — only {@code ACTIVE}/{@code PAST_DUE}-like subscriptions can enter dunning).
     * Postcondition: schedules the first retry attempt (attempt #1) at
     * {@code now + DunningPolicy.day1RetryAfterHours()} and records a
     * {@code DUNNING_STARTED} history entry and domain event.
     */
    void startDunning(UUID subscriptionId, UUID invoiceId);

    /**
     * Scheduler entry point: executes every dunning attempt whose {@code nextRetryAt} is due.
     *
     * @implSpec Intended to be invoked periodically by a scheduler/cron trigger, not directly
     * by request-handling code. Per-attempt failures (including races with another scheduler
     * node processing the same attempt, surfaced as {@code ConcurrentUpdateException}) are
     * caught and logged individually — one attempt failing does not abort the batch. Each due
     * attempt advances through the dunning ladder (retry → suspend → cancel) according to
     * {@code DunningPolicy}; a subscription that has already left dunning (recovered or
     * cancelled) causes its stale attempt to be marked resolved without a payment call.
     */
    void processDueAttempts();

    /**
     * Manually forces an immediate retry of the latest dunning attempt for a subscription
     * (e.g. an operator-triggered "retry now").
     *
     * @implSpec Requires the subscription to exist and to have a prior dunning attempt in
     * {@code PENDING} or {@code FAILED} status, else throws
     * {@link com.company.bsmsvc.domain.exception.BusinessRuleViolationException}. Requires
     * tenant-scope access to the subscription's tenant.
     */
    void manualRetry(UUID subscriptionId);

    /** Returns the most recent dunning attempt for the subscription, or {@code null} if none exist. */
    DunningAttempt getLatestAttempt(UUID subscriptionId);

    /**
     * Returns the full dunning-attempt history for a subscription.
     *
     * @implSpec Requires the subscription to exist and enforces tenant-scope access to it.
     */
    List<DunningAttempt> getAttempts(UUID subscriptionId);

    /**
     * Called when a payment succeeds through an external channel (webhook, checkout session)
     * while the subscription is still in a dunning state. Recovers the subscription without
     * issuing a new charge — the money is already collected.
     *
     * <p>Idempotent: if the subscription is not currently in dunning the call is a no-op.</p>
     */
    void recoveryPaymentReceived(UUID subscriptionId, UUID invoiceId);
}
