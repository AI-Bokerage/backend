package com.company.bsmsvc.application.service;

import com.company.bsmsvc.domain.model.PaymentMethod;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import java.util.List;
import java.util.UUID;

/**
 * Manages the tenant's provider-side customer record and saved payment methods.
 *
 * <p>This is the service that establishes the gateway-customer record
 * ({@code TenantBillingProfile.externalCustomerId}) which {@link PaymentService#createPaymentIntent}
 * and {@link PaymentService#createCheckoutSession} require. Call {@link #createCustomerIfRequired}
 * (or simply {@link #addPaymentMethod}, which creates the customer implicitly if missing) before
 * attempting to charge a tenant.
 */
public interface PaymentMethodService {

    /**
     * Ensures a provider (Stripe/Razorpay) customer exists for the tenant, creating one via the
     * gateway if {@code TenantBillingProfile.externalCustomerId} is not already set.
     *
     * @implSpec Idempotent: if the profile already has an external customer id, it is returned
     * unchanged with no gateway call. This is the explicit call-first step of the "register a
     * payment method" flow; {@link #addPaymentMethod} performs the same creation implicitly if
     * this was skipped, so calling it directly is optional but recommended for clarity/ordering.
     * Establishes the precondition that {@link PaymentService#createPaymentIntent} and
     * {@link PaymentService#createCheckoutSession} depend on.
     */
    TenantBillingProfile createCustomerIfRequired(UUID tenantId, String name, String email);

    /**
     * Attaches a tokenized payment method to the tenant's provider customer and records it locally.
     *
     * @implSpec Should logically follow {@link #createCustomerIfRequired}, though it is safe to
     * call directly: if no valid provider customer id exists yet for the tenant, one is created
     * first (same effect as calling {@link #createCustomerIfRequired}) within this method's own
     * transaction. Rejects duplicate tokens for the same tenant with
     * {@link com.company.bsmsvc.domain.exception.BusinessRuleViolationException}. If
     * {@code makeDefault} is true, clears the default flag on any other payment method for the
     * tenant first. On success, this is the operation that ultimately satisfies
     * {@link PaymentService}'s "provider customer must exist" precondition.
     */
    PaymentMethod addPaymentMethod(UUID tenantId, String paymentMethodToken, com.company.bsmsvc.domain.enums.PaymentMethodType type, String brand, String lastFour, Integer expMonth, Integer expYear, boolean makeDefault);

    /** Lists all payment methods (active and detached) recorded for the tenant. */
    List<PaymentMethod> listPaymentMethods(UUID tenantId);

    /**
     * Detaches a payment method from the provider and marks it removed locally.
     *
     * @implSpec {@code id} is the payment-method id (not the tenant id) — tenant scope is
     * derived from the stored entity itself. Requires the tenant's billing profile (and thus
     * its external customer id) to still exist.
     */
    void removePaymentMethod(UUID id);

    /**
     * Marks a payment method as the tenant's default, clearing the default flag on any other
     * payment method for the same tenant.
     *
     * @implSpec {@code id} is the payment-method id — tenant scope is derived from the stored
     * entity. Throws {@link com.company.bsmsvc.domain.exception.BusinessRuleViolationException}
     * if the payment method has already been detached.
     */
    PaymentMethod setDefaultPaymentMethod(UUID id);
}
