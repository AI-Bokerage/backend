package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.application.service.PaymentService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.InvoiceStatus;
import com.company.bsmsvc.domain.enums.PaymentStatus;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.exception.PaymentNotFoundException;
import com.company.bsmsvc.domain.model.Payment;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.model.payment.CheckoutSessionResult;
import com.company.bsmsvc.domain.model.payment.CreateCheckoutSessionCommand;
import com.company.bsmsvc.domain.model.payment.CreatePaymentIntentCommand;
import com.company.bsmsvc.domain.model.payment.PaymentIntentResult;
import com.company.bsmsvc.domain.port.PaymentGatewayPort;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import com.company.bsmsvc.domain.port.EventPublisherPort;
import com.company.bsmsvc.domain.port.TenantScopePort;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final TenantBillingProfileService billingProfileService;
    private final PaymentGatewayResolver resolver;
    private final PaymentRepositoryPort paymentRepository;
    private final PlatformInvoiceRepositoryPort invoiceRepository;
    private final TenantScopePort tenantScopeEnforcer;
    private final EventPublisherPort auditEventPublisher;

    @Override
    // No @Transactional here — the Stripe/Razorpay API call can take 1-3 seconds.
    // Holding a DB transaction open during that window causes optimistic-lock conflicts
    // when concurrent jobs (dunning scheduler, webhook processing, reconciliation) modify
    // shared entities. Each individual DB operation uses its own short transaction.
    public CheckoutSessionResult createCheckoutSession(UUID tenantId, UUID invoiceId, String successUrl, String cancelUrl) {
        tenantScopeEnforcer.assertTenantAccess(tenantId);
        PlatformInvoice invoice = getPayableInvoice(invoiceId, tenantId);
        TenantBillingProfile profile = billingProfileService.getProfile(tenantId);
        validateCustomerExists(profile, tenantId);

        Map<String, String> metadata = buildMetadata(tenantId, invoiceId, invoice.getSubscriptionId());
        String description = "Invoice " + invoice.getInvoiceNumber()
            + " — " + invoice.getAmountDue() + " " + invoice.getCurrency().toUpperCase();

        PaymentGatewayPort gateway = resolver.resolve(profile.getPaymentProvider());
        // Stripe/Razorpay API call — runs outside any DB transaction
        CheckoutSessionResult result = gateway.createCheckoutSession(new CreateCheckoutSessionCommand(
            profile.getExternalCustomerId(), successUrl, cancelUrl,
            invoice.getAmountDue(), invoice.getCurrency(), description, metadata
        ));

        persistPaymentRecord(tenantId, invoiceId, profile, result.sessionId(), invoice);
        log.info("createCheckoutSession invoiceId={} sessionId={} amount={} currency={}",
            invoiceId, result.sessionId(), invoice.getAmountDue(), invoice.getCurrency());
        return result;
    }

    @Override
    // No @Transactional here — same reason as createCheckoutSession
    public PaymentIntentResult createPaymentIntent(UUID tenantId, UUID invoiceId) {
        tenantScopeEnforcer.assertTenantAccess(tenantId);
        PlatformInvoice invoice = getPayableInvoice(invoiceId, tenantId);
        TenantBillingProfile profile = billingProfileService.getProfile(tenantId);
        validateCustomerExists(profile, tenantId);

        Map<String, String> metadata = buildMetadata(tenantId, invoiceId, invoice.getSubscriptionId());

        PaymentGatewayPort gateway = resolver.resolve(profile.getPaymentProvider());
        // Stripe/Razorpay API call — runs outside any DB transaction
        PaymentIntentResult result = gateway.createPaymentIntent(new CreatePaymentIntentCommand(
            tenantId,
            profile.getExternalCustomerId(),
            invoice.getAmountDue(),
            invoice.getCurrency(),
            metadata
        ));

        persistPaymentRecord(tenantId, invoiceId, profile, result.paymentIntentId(), invoice);
        log.info("createPaymentIntent invoiceId={} intentId={} amount={} currency={}",
            invoiceId, result.paymentIntentId(), invoice.getAmountDue(), invoice.getCurrency());
        return result;
    }

    @Override
    public Payment getPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
        tenantScopeEnforcer.assertTenantAccess(payment.getTenantId());
        return payment;
    }

    private PlatformInvoice getPayableInvoice(UUID invoiceId, UUID tenantId) {
        PlatformInvoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new com.company.bsmsvc.domain.exception.InvoiceNotFoundException("Invoice not found: " + invoiceId));
        if (!invoice.getTenantId().equals(tenantId)) {
            throw new BusinessRuleViolationException("Invoice does not belong to tenant: " + tenantId);
        }
        if (invoice.getStatus() != InvoiceStatus.OPEN) {
            throw new BusinessRuleViolationException("Invoice is not payable. Status: " + invoice.getStatus());
        }
        if (invoice.getAmountDue() <= 0) {
            throw new BusinessRuleViolationException("Invoice amount must be greater than zero");
        }
        return invoice;
    }

    private void validateCustomerExists(TenantBillingProfile profile, UUID tenantId) {
        if (profile.getExternalCustomerId() == null || profile.getExternalCustomerId().isBlank()) {
            throw new BusinessRuleViolationException(
                "No provider customer for tenant: " + tenantId + ". Add a payment method first.");
        }
    }

    private Map<String, String> buildMetadata(UUID tenantId, UUID invoiceId, UUID subscriptionId) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("tenantId", tenantId.toString());
        metadata.put("invoiceId", invoiceId.toString());
        if (subscriptionId != null) metadata.put("subscriptionId", subscriptionId.toString());
        return metadata;
    }

    @Transactional
    public void persistPaymentRecord(UUID tenantId, UUID invoiceId, TenantBillingProfile profile,
                                     String externalPaymentId, PlatformInvoice invoice) {
        try {
            Payment saved = paymentRepository.save(Payment.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .invoiceId(invoiceId)
                .paymentProvider(profile.getPaymentProvider())
                .externalPaymentId(externalPaymentId)
                .status(PaymentStatus.PENDING)
                .amountMinor(invoice.getAmountDue())
                .currency(invoice.getCurrency())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
            // NEW audit-only leg — no business-exchange presence exists for payment.* at all
            // today (PaymentInitiatedEvent etc. are dead code); HTTP-driven, no actor threaded.
            Map<String, Object> data = new HashMap<>();
            data.put("paymentId", saved.getId().toString());
            data.put("invoiceId", invoiceId.toString());
            data.put("externalPaymentId", externalPaymentId);
            data.put("amountMinor", invoice.getAmountDue());
            data.put("currency", invoice.getCurrency());
            auditEventPublisher.publish("payment.created", tenantId, "Payment", saved.getId(), null, data);
        } catch (DataIntegrityViolationException ex) {
            // externalPaymentId already recorded — idempotent, nothing to do
            log.warn("persistPaymentRecord: externalPaymentId={} already exists, skipping duplicate save", externalPaymentId);
        }
    }
}
