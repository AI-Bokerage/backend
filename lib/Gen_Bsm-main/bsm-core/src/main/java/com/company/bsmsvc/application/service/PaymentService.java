package com.company.bsmsvc.application.service;

import com.company.bsmsvc.domain.model.Payment;
import com.company.bsmsvc.domain.model.payment.CheckoutSessionResult;
import com.company.bsmsvc.domain.model.payment.PaymentIntentResult;
import java.util.UUID;

/**
 * Collects payment against an already-{@code OPEN} {@link com.company.bsmsvc.domain.model.PlatformInvoice}.
 *
 * <p>Both charging methods below depend transitively on {@link PaymentMethodService}: a tenant
 * must already have a provider customer (and, in practice, a payment method) registered before
 * either method can succeed. Neither method creates that customer itself.
 */
public interface PaymentService {

    /**
     * Creates a hosted checkout session (redirect-based flow) for the given invoice.
     *
     * @implSpec Requires a provider customer to already exist for the tenant — i.e.
     * {@link PaymentMethodService#createCustomerIfRequired} (typically via
     * {@link PaymentMethodService#addPaymentMethod}) must have run first and populated
     * {@code TenantBillingProfile.externalCustomerId}. If it has not, this throws
     * {@link com.company.bsmsvc.domain.exception.BusinessRuleViolationException} ("No provider
     * customer for tenant"). The invoice must belong to the tenant, be in {@code OPEN} status,
     * and have a positive amount due, or a {@code BusinessRuleViolationException} is thrown.
     * On success, persists a {@code PENDING} {@link Payment} record linked to the returned
     * session id.
     */
    CheckoutSessionResult createCheckoutSession(UUID tenantId, UUID invoiceId, String successUrl, String cancelUrl);

    /**
     * Creates a provider PaymentIntent (API-driven / embedded flow) for the given invoice.
     *
     * @implSpec Same precondition as {@link #createCheckoutSession}: the tenant must already
     * have a provider customer registered via {@link PaymentMethodService}, otherwise this
     * throws {@link com.company.bsmsvc.domain.exception.BusinessRuleViolationException} ("No
     * provider customer for tenant ... Add a payment method first."). The invoice must belong
     * to the tenant, be {@code OPEN}, and have a positive amount due. On success, persists a
     * {@code PENDING} {@link Payment} record linked to the returned payment-intent id.
     */
    PaymentIntentResult createPaymentIntent(UUID tenantId, UUID invoiceId);

    /**
     * Looks up a previously-created payment by id, enforcing that it belongs to the
     * caller's tenant scope.
     */
    Payment getPayment(UUID paymentId);
}
