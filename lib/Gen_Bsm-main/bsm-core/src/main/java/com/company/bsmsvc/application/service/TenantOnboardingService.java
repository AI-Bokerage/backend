package com.company.bsmsvc.application.service;

import com.company.bsmsvc.domain.model.OnboardTenantCommand;

/**
 * Onboards a newly-created tenant into a BSM subscription — TRIALING only if
 * the resolved plan is the free/trial plan, ACTIVE immediately for paid plans.
 *
 * <p><strong>Idempotency:</strong> if an active subscription already exists for
 * the tenant, the command is silently skipped — safe for retries and duplicate
 * deliveries.</p>
 */
public interface TenantOnboardingService {

    /**
     * Onboards the tenant identified by {@link OnboardTenantCommand#tenantId()}.
     *
     * @implSpec Expected call-order position: this is the entry point that seeds a tenant's
     * <em>first</em> subscription; it must run before any {@link SubscriptionService},
     * {@link InvoiceService}, or {@link PaymentService} call for that tenant, and is a no-op
     * (see idempotency above) if a subscription already exists.
     * <p>{@code ppmPlanId}/{@code ppmPlanVersionId} are optional together: if both are
     * non-null, that pre-resolved plan version is used directly (its tier is then looked up via
     * {@link com.company.bsmsvc.domain.port.PlanVersionMetaPort} to decide trial vs. paid); if
     * either is null, the platform's default trial plan is resolved via
     * {@link com.company.bsmsvc.domain.port.DefaultTrialPlanPort#getDefaultTrialPlan()} and
     * treated as trial by definition. {@code region} defaults to {@code INDIA} and
     * {@code billingCycle} defaults to {@code MONTHLY} when not supplied or unparsable.
     * <p>Failure handling: if PPM (plan-tier or default-trial-plan resolution) is unreachable,
     * this throws {@link IllegalStateException} so the caller's messaging adapter can retry
     * after backoff — it does not silently guess a plan tier.
     * <p>Postconditions: creates the subscription via {@link SubscriptionService#createSubscription}
     * (auto-creating the tenant's {@code TenantBillingProfile} first if pricing needs to be
     * resolved for a paid plan), then generates a first invoice via
     * {@link InvoiceGenerationService#generateInvoice} — a {@code $0} invoice marked
     * {@code PAID} immediately for the trial plan, or (for paid plans) marked {@code PAID}
     * immediately only when {@code sourceChannel} is {@code SELF_SIGNUP} (payment already
     * collected upstream), otherwise left {@code OPEN} for reconciliation.
     */
    void onboard(OnboardTenantCommand command);
}
