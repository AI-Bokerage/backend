package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.InvoiceStatus;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.enums.PaymentStatus;
import com.company.bsmsvc.domain.model.Payment;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.model.payment.CheckoutSessionResult;
import com.company.bsmsvc.domain.model.payment.PaymentStatusResult;
import com.company.bsmsvc.domain.port.PaymentGatewayPort;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import com.company.bsmsvc.domain.port.EventPublisherPort;
import com.company.bsmsvc.domain.port.TenantScopePort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Confirms the real payment lifecycle events actually wired in this phase:
 * {@code payment.created} ({@link PaymentServiceImpl#persistPaymentRecord}) and
 * {@code payment.captured}/{@code payment.failed} ({@link
 * PaymentReconciliationServiceImpl#reconcilePayment}). {@code payment.authorized} and
 * {@code payment.reversed} have NO corresponding real code path in bsm-svc today (no separate
 * "authorize" step exists in the checkout/payment-intent flow, and no "reversed" transition exists
 * on {@code Payment} — only {@code REFUNDED}, which is the refund.* family's concern) — reported
 * here, not fabricated.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Payment audit publish — real call sites only")
class PaymentAuditPublishTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID invoiceId = UUID.randomUUID();

    @Mock private TenantBillingProfileService billingProfileService;
    @Mock private PaymentGatewayResolver resolver;
    @Mock private PaymentRepositoryPort paymentRepository;
    @Mock private PlatformInvoiceRepositoryPort invoiceRepository;
    @Mock private PaymentGatewayPort gateway;
    @Mock private TenantScopePort tenantScopeEnforcer;
    @Mock private EventPublisherPort auditEventPublisher;
    @InjectMocks private PaymentServiceImpl paymentService;

    @Test
    @DisplayName("createCheckoutSession → persistPaymentRecord enqueues payment.created (no actor, HTTP-driven)")
    void checkoutSession_enqueuesPaymentCreated() {
        when(tenantScopeEnforcer.resolveEffectiveTenantId(any())).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice(InvoiceStatus.OPEN)));
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile("cus_123"));
        when(resolver.resolve(any())).thenReturn(gateway);
        when(gateway.createCheckoutSession(any()))
            .thenReturn(new CheckoutSessionResult("cs_test", "https://checkout.stripe.com/cs_test"));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentService.createCheckoutSession(tenantId, invoiceId, "https://ok", "https://cancel");

        verify(auditEventPublisher).publish(eq("payment.created"), eq(tenantId), eq("Payment"),
            any(), org.mockito.Mockito.isNull(), any());
    }

    private PlatformInvoice invoice(InvoiceStatus status) {
        return PlatformInvoice.builder()
            .id(invoiceId).tenantId(tenantId).subscriptionId(UUID.randomUUID())
            .invoiceNumber("INV-001").status(status)
            .amountDue(10000L).amountPaid(0L).currency("INR")
            .periodStart(Instant.now()).periodEnd(Instant.now().plusSeconds(86400))
            .dueDate(LocalDate.now().plusDays(30))
            .lineItems(new ArrayList<>()).domainEvents(new ArrayList<>()).build();
    }

    private TenantBillingProfile profile(String customerId) {
        return TenantBillingProfile.builder()
            .id(UUID.randomUUID()).tenantId(tenantId)
            .paymentProvider(PaymentProvider.STRIPE).externalCustomerId(customerId)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .domainEvents(new ArrayList<>()).build();
    }

    // ── Reconciliation: captured / failed ──────────────────────────────────────

    @org.junit.jupiter.api.Nested
    @DisplayName("PaymentReconciliationServiceImpl")
    class Reconciliation {

        @Mock private PaymentRepositoryPort paymentRepository;
        @Mock private PaymentGatewayResolver resolver;
        @Mock private TenantBillingProfileService billingProfileService;
        @Mock private com.company.bsmsvc.application.service.InvoiceService invoiceService;
        @Mock private com.company.bsmsvc.domain.port.BillingLedgerRepositoryPort ledgerRepository;
        @Mock private com.company.bsmsvc.config.ReconciliationProperties properties;
        @Mock private PaymentGatewayPort gateway;
        @Mock private EventPublisherPort auditEventPublisher;
        @InjectMocks private PaymentReconciliationServiceImpl reconciliationService;

        private Payment pendingPayment() {
            return Payment.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).invoiceId(invoiceId)
                .paymentProvider(PaymentProvider.STRIPE).externalPaymentId("cs_test")
                .status(PaymentStatus.PENDING).amountMinor(10000L).currency("INR")
                .createdAt(Instant.now().minusSeconds(600)).updatedAt(Instant.now())
                .build();
        }

        @BeforeEach
        void setUp() {
            when(resolver.resolve(any())).thenReturn(gateway);
            when(billingProfileService.getProfile(any())).thenReturn(profile("cus_123"));
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("provider succeeded → payment.captured audit leg")
        void reconcilePayment_succeeded_enqueuesPaymentCaptured() {
            Payment payment = pendingPayment();
            when(gateway.retrievePaymentStatus("cs_test"))
                .thenReturn(new PaymentStatusResult("cs_test", "complete", "ch_xxx", true, false));

            reconciliationService.reconcilePayment(payment);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            verify(auditEventPublisher).publish(eq("payment.captured"), eq(tenantId), eq("Payment"),
                eq(payment.getId()), org.mockito.Mockito.isNull(), any());
        }

        @Test
        @DisplayName("provider failed → payment.failed audit leg")
        void reconcilePayment_failed_enqueuesPaymentFailed() {
            Payment payment = pendingPayment();
            when(gateway.retrievePaymentStatus("cs_test"))
                .thenReturn(new PaymentStatusResult("cs_test", "expired", null, false, true));

            reconciliationService.reconcilePayment(payment);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            verify(auditEventPublisher).publish(eq("payment.failed"), eq(tenantId), eq("Payment"),
                eq(payment.getId()), org.mockito.Mockito.isNull(), any());
        }
    }
}
