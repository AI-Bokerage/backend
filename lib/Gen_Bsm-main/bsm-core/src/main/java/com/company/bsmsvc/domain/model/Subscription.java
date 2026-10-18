package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.DunningStatus;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.event.DunningCancelledEvent;
import com.company.bsmsvc.domain.event.DunningRecoveredEvent;
import com.company.bsmsvc.domain.event.DunningStartedEvent;
import com.company.bsmsvc.domain.event.DunningSuspendedEvent;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

    private UUID id;
    private UUID tenantId;
    private UUID planVersionId;
    // PPM catalog identifiers locked at checkout (C2 grandfathering). Null for
    // subscriptions created through the native BSM path.
    private UUID ppmPlanId;
    private UUID ppmPriceId;
    private UUID ppmPlanVersionId;
    private Long ppmResolvedPriceMinor;
    private SubscriptionStatus status;
    private BillingCycle billingCycle;
    private Instant currentPeriodStart;
    private Instant currentPeriodEnd;
    private Instant trialEndsAt;
    private Instant cancelledAt;
    private boolean cancelAtPeriodEnd;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
    private PaymentProvider paymentProvider;
    private String externalSubscriptionId;
    private DunningStatus dunningStatus;
    private Instant dunningStartedAt;
    private Instant dunningNextActionAt;

    // FIX 14: internal domain-event collection; excluded from persistence layer via mapper ignore
    @Builder.Default
    private List<Object> domainEvents = new ArrayList<>();

    // ---- State-machine methods ----

    public void activate() {
        if (status == SubscriptionStatus.ACTIVE) {
            return;
        }
        if (status != SubscriptionStatus.TRIALING && status != SubscriptionStatus.PAUSED
            && status != SubscriptionStatus.PAST_DUE && status != SubscriptionStatus.SUSPENDED_PENDING_PURGE) {
            throw new BusinessRuleViolationException("Transition to ACTIVE is not allowed from status: " + status);
        }
        this.status = SubscriptionStatus.ACTIVE;
    }

    public void pause() {
        if (status == SubscriptionStatus.PAUSED) {
            return;
        }
        if (status != SubscriptionStatus.ACTIVE) {
            throw new BusinessRuleViolationException("Transition to PAUSED is not allowed from status: " + status);
        }
        this.status = SubscriptionStatus.PAUSED;
    }

    public void resume() {
        if (status == SubscriptionStatus.ACTIVE) {
            return;
        }
        if (status != SubscriptionStatus.PAUSED) {
            throw new BusinessRuleViolationException("Transition to ACTIVE via resume is not allowed from status: " + status);
        }
        this.status = SubscriptionStatus.ACTIVE;
    }

    public void markPastDue() {
        if (status == SubscriptionStatus.PAST_DUE) {
            return;
        }
        if (status != SubscriptionStatus.ACTIVE) {
            throw new BusinessRuleViolationException("Transition to PAST_DUE is not allowed from status: " + status);
        }
        this.status = SubscriptionStatus.PAST_DUE;
    }

    public void scheduleCancellation(Instant when) {
        ensureCancellableState();
        this.cancelAtPeriodEnd = true;
        this.cancelledAt = when;
    }

    public void cancel(Instant when) {
        if (status == SubscriptionStatus.CANCELLED) {
            return;
        }
        ensureCancellableState();
        this.cancelAtPeriodEnd = false;
        this.cancelledAt = when;
        this.status = SubscriptionStatus.CANCELLED;
    }

    // FIX 12: expiry helpers for external callers (no scheduler dependency)
    public void advanceBillingPeriod(Instant newPeriodStart, Instant newPeriodEnd) {
        this.currentPeriodStart = newPeriodStart;
        this.currentPeriodEnd   = newPeriodEnd;
    }

    public boolean isExpired() {
        return currentPeriodEnd != null && currentPeriodEnd.isBefore(Instant.now());
    }

    public boolean requiresRenewal() {
        return isExpired() && status == SubscriptionStatus.ACTIVE;
    }

    // FIX 14: domain event aggregation — internal collection, no publishing
    public void registerEvent(Object event) {
        if (this.domainEvents == null) {
            this.domainEvents = new ArrayList<>();
        }
        this.domainEvents.add(event);
    }

    public List<Object> pullDomainEvents() {
        if (this.domainEvents == null || this.domainEvents.isEmpty()) {
            return Collections.emptyList();
        }
        List<Object> snapshot = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return snapshot;
    }

    public void clearDomainEvents() {
        if (this.domainEvents != null) {
            this.domainEvents.clear();
        }
    }

    // ---- Dunning state-machine methods ----

    public void startDunning(UUID invoiceId, Instant nextRetryAt) {
        if (this.dunningStatus != null && this.dunningStatus != DunningStatus.NORMAL) return;
        this.status = SubscriptionStatus.PAST_DUE;
        this.dunningStatus = DunningStatus.DUNNING_DAY_1;
        this.dunningStartedAt = Instant.now();
        this.dunningNextActionAt = nextRetryAt;
        registerEvent(new DunningStartedEvent(this.id, this.tenantId, invoiceId, 1, Instant.now()));
    }

    public void advanceDunningAfterFailure(DunningStatus next, Instant nextActionAt) {
        this.dunningStatus = next;
        this.dunningNextActionAt = nextActionAt;
        if (next == DunningStatus.SUSPENDED_PENDING_PURGE) {
            this.status = SubscriptionStatus.SUSPENDED_PENDING_PURGE;
            registerEvent(new DunningSuspendedEvent(this.id, this.tenantId, Instant.now()));
        }
    }

    public void recoverFromDunning() {
        this.status = SubscriptionStatus.ACTIVE;
        this.dunningStatus = DunningStatus.NORMAL;
        this.dunningStartedAt = null;
        this.dunningNextActionAt = null;
        registerEvent(new DunningRecoveredEvent(this.id, this.tenantId, null, Instant.now()));
    }

    public void cancelFromDunning(Instant when) {
        this.status = SubscriptionStatus.CANCELLED;
        this.dunningStatus = DunningStatus.CANCELLED;
        this.cancelledAt = when;
        this.cancelAtPeriodEnd = false;
        this.dunningNextActionAt = null;
        registerEvent(new DunningCancelledEvent(this.id, this.tenantId, Instant.now()));
    }

    /**
     * Cancels the subscription from an external signal (e.g. Stripe dashboard) and atomically
     * clears all dunning state in a single DB write.  Without clearing dunning fields the
     * DunningScheduler would continue retrying payments for a subscription that is already
     * cancelled, and a successful retry would incorrectly reset status to ACTIVE.
     */
    public void cancelAndClearDunning(Instant when) {
        if (this.status == SubscriptionStatus.CANCELLED) {
            // Already cancelled — still ensure dunning state is cleared for idempotency
            this.dunningStatus = DunningStatus.CANCELLED;
            this.dunningNextActionAt = null;
            return;
        }
        ensureCancellableState();
        this.status = SubscriptionStatus.CANCELLED;
        this.cancelAtPeriodEnd = false;
        this.cancelledAt = when;
        this.dunningStatus = DunningStatus.CANCELLED;
        this.dunningStartedAt = null;
        this.dunningNextActionAt = null;
    }

    public boolean isInDunning() {
        return dunningStatus != null && dunningStatus != DunningStatus.NORMAL && dunningStatus != DunningStatus.CANCELLED;
    }

    private void ensureCancellableState() {
        if (status != SubscriptionStatus.TRIALING
            && status != SubscriptionStatus.ACTIVE
            && status != SubscriptionStatus.PAUSED
            && status != SubscriptionStatus.PAST_DUE
            && status != SubscriptionStatus.SUSPENDED_PENDING_PURGE) {
            throw new BusinessRuleViolationException("Cancellation is not allowed from status: " + status);
        }
    }
}
