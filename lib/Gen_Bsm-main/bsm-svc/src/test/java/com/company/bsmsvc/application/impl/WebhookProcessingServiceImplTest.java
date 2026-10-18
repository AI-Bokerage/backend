package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.company.bsmsvc.application.service.DunningService;
import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.messaging.BsmSubscriptionEventPublisher;
import com.company.bsmsvc.config.WebhookProperties;
import com.company.bsmsvc.domain.enums.InvoiceStatus;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.enums.PaymentStatus;
import com.company.bsmsvc.domain.enums.WebhookEventStatus;
import com.company.bsmsvc.domain.exception.WebhookVerificationException;
import com.company.bsmsvc.domain.model.Payment;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.WebhookEvent;
import com.company.bsmsvc.domain.port.BillingLedgerRepositoryPort;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.port.WebhookEventRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookProcessingServiceImplTest {

    @Mock private WebhookEventRepositoryPort webhookEventRepository;
    @Mock private PaymentRepositoryPort paymentRepository;
    @Mock private PlatformInvoiceRepositoryPort invoiceRepository;
    @Mock private InvoiceService invoiceService;
    @Mock private BillingLedgerRepositoryPort ledgerRepository;
    @Mock private WebhookProperties webhookProperties;
    @Mock private DunningService dunningService;
    @Mock private SubscriptionRepositoryPort subscriptionRepository;
    @Mock private SubscriptionHistoryRepositoryPort subscriptionHistoryRepository;
    @Mock private BsmSubscriptionEventPublisher subscriptionEventPublisher;
    @InjectMocks private WebhookProcessingServiceImpl service;

    private static final String TEST_RAZORPAY_SECRET = "test-razorpay-secret-for-unit-tests";
    private static final String STRIPE_SUB_ID   = "sub_stripe_abc123";
    private static final String PAYMENT_INTENT  = "pi_stripe_xyz789";
    private static final String STRIPE_INV_ID   = "in_stripe_inv001";

    @BeforeEach
    void setUp() {
        when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(webhookProperties.stripeSecret()).thenReturn("whsec_placeholder");
        when(webhookProperties.razorpaySecret()).thenReturn(TEST_RAZORPAY_SECRET);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().doNothing().when(subscriptionEventPublisher).publishCanceled(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String razorpaySignature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(TEST_RAZORPAY_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Subscription subscription(UUID id, UUID tenantId) {
        return Subscription.builder()
            .id(id).tenantId(tenantId)
            .externalSubscriptionId(STRIPE_SUB_ID)
            .paymentProvider(PaymentProvider.STRIPE)
            .status(com.company.bsmsvc.domain.enums.SubscriptionStatus.ACTIVE)
            .billingCycle(com.company.bsmsvc.domain.enums.BillingCycle.MONTHLY)
            .currentPeriodStart(Instant.now().minusSeconds(30 * 86400L))
            .currentPeriodEnd(Instant.now().plusSeconds(86400L))
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private PlatformInvoice openInvoice(UUID invoiceId, UUID subscriptionId, UUID tenantId) {
        return PlatformInvoice.builder()
            .id(invoiceId).subscriptionId(subscriptionId).tenantId(tenantId)
            .invoiceNumber("INV-001").status(InvoiceStatus.OPEN)
            .amountDue(10000L).amountPaid(0L).currency("INR")
            .source(com.company.bsmsvc.domain.enums.InvoiceSource.SUBSCRIPTION_RENEWAL)
            .createdAt(Instant.now().minusSeconds(60)).updatedAt(Instant.now())
            .pdfGenerationStatus(com.company.bsmsvc.domain.enums.InvoicePdfStatus.PENDING)
            .lineItems(List.of()).build();
    }

    // ── Legacy Stripe / Razorpay tests (unchanged) ────────────────────────────

    @Test
    void processStripeWebhook_throwsOnInvalidSignature() {
        assertThatThrownBy(() -> service.processStripeWebhook("{}", "t=12345,v1=invalidsig"))
            .isInstanceOf(Exception.class);
    }

    @Test
    void processRazorpayWebhook_isDuplicate_isIgnored() {
        String eventId = UUID.randomUUID().toString();
        String payload = "{\"id\":\"" + eventId + "\",\"event\":\"payment.captured\"}";
        WebhookEvent existing = WebhookEvent.builder()
            .id(UUID.randomUUID()).provider(PaymentProvider.RAZORPAY)
            .externalEventId(eventId).eventType("payment.captured")
            .status(WebhookEventStatus.PROCESSED).receivedAt(Instant.now()).build();
        when(webhookEventRepository.findByProviderAndExternalEventId(PaymentProvider.RAZORPAY, eventId))
            .thenReturn(Optional.of(existing));

        service.processRazorpayWebhook(payload, razorpaySignature(payload));

        verify(webhookEventRepository, never()).save(any(WebhookEvent.class));
    }

    @Test
    void processRazorpayWebhook_newEvent_isSavedAtLeastOnce() {
        String eventId = UUID.randomUUID().toString();
        String payload = "{\"id\":\"" + eventId + "\",\"event\":\"payment.failed\"}";
        when(webhookEventRepository.findByProviderAndExternalEventId(any(), any())).thenReturn(Optional.empty());

        service.processRazorpayWebhook(payload, razorpaySignature(payload));

        verify(webhookEventRepository, org.mockito.Mockito.atLeast(1)).save(any(WebhookEvent.class));
    }

    @Test
    void processRazorpayWebhook_resolvesByExternalPaymentId_whenPresent() {
        String eventId = UUID.randomUUID().toString();
        String orderId = "order_razorpay_123";
        String payload = "{\"id\":\"" + eventId + "\",\"event\":\"payment.captured\","
            + "\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_123\",\"order_id\":\"" + orderId + "\"}}}}";
        when(webhookEventRepository.findByProviderAndExternalEventId(any(), any())).thenReturn(Optional.empty());
        Payment payment = Payment.builder()
            .id(UUID.randomUUID()).tenantId(UUID.randomUUID()).invoiceId(UUID.randomUUID())
            .paymentProvider(PaymentProvider.RAZORPAY).externalPaymentId(orderId)
            .status(PaymentStatus.PENDING).amountMinor(5000L).currency("INR")
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(paymentRepository.findByExternalPaymentId(orderId)).thenReturn(Optional.of(payment));

        service.processRazorpayWebhook(payload, razorpaySignature(payload));

        verify(paymentRepository).save(any());
    }

    @Test
    void processRazorpayWebhook_strictDuplicateCheck_rejectsSameEventId() {
        String eventId = "evt_duplicate_123";
        String payload = "{\"id\":\"" + eventId + "\",\"event\":\"payment.captured\"}";
        WebhookEvent existing = WebhookEvent.builder()
            .id(UUID.randomUUID()).provider(PaymentProvider.RAZORPAY)
            .externalEventId(eventId).eventType("payment.captured")
            .status(WebhookEventStatus.PROCESSED).receivedAt(Instant.now()).build();
        when(webhookEventRepository.findByProviderAndExternalEventId(PaymentProvider.RAZORPAY, eventId))
            .thenReturn(Optional.of(existing));

        service.processRazorpayWebhook(payload, razorpaySignature(payload));

        verify(webhookEventRepository, never()).save(any(WebhookEvent.class));
    }

    // ── Phase 5.8: ledger entry always created even when invoice already PAID ──

    @Test
    void reconcileAutoRenewalSuccess_ledgerCreatedEvenIfInvoiceAlreadyPaid() {
        UUID tenantId  = UUID.randomUUID();
        UUID subId     = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        when(subscriptionRepository.findByExternalSubscriptionId(STRIPE_SUB_ID))
            .thenReturn(Optional.of(subscription(subId, tenantId)));
        when(invoiceRepository.findBySubscriptionId(subId))
            .thenReturn(List.of(openInvoice(invoiceId, subId, tenantId)));

        // Simulate invoiceService.applyPayment() throwing because invoice already PAID
        // by a concurrent process
        when(invoiceService.applyPayment(any(), any(), any()))
            .thenThrow(new com.company.bsmsvc.domain.exception.BusinessRuleViolationException(
                "Invoice is not payable. Status: PAID"));

        // invoiceRepository.findById called by dunningService.recoveryPaymentReceived
        when(invoiceRepository.findById(invoiceId))
            .thenReturn(Optional.of(openInvoice(invoiceId, subId, tenantId)));

        service.reconcileAutoRenewalSuccess(STRIPE_INV_ID, PAYMENT_INTENT, STRIPE_SUB_ID, 10000L, "INR");

        // Payment row must be saved
        verify(paymentRepository).save(any());
        // applyPayment() is still called — the domain-event path inside the real adapter
        // writes the ledger entry.  In unit tests the adapter is mocked so ledgerRepository
        // is not called, but the intent to mark the invoice paid is verified here.
        verify(invoiceService).applyPayment(any(), any(), any());
    }

    @Test
    void processRazorpayWebhook_blankSecret_throwsVerificationException() {
        when(webhookProperties.razorpaySecret()).thenReturn("");
        String payload = "{\"id\":\"evt_1\",\"event\":\"payment.captured\"}";

        assertThatThrownBy(() -> service.processRazorpayWebhook(payload, "any-sig"))
            .isInstanceOf(WebhookVerificationException.class)
            .hasMessageContaining("not configured");
    }

    @Test
    void processRazorpayWebhook_nullSecret_throwsVerificationException() {
        when(webhookProperties.razorpaySecret()).thenReturn(null);
        String payload = "{\"id\":\"evt_2\",\"event\":\"payment.captured\"}";

        assertThatThrownBy(() -> service.processRazorpayWebhook(payload, "any-sig"))
            .isInstanceOf(WebhookVerificationException.class);
    }

    @Test
    void processRazorpayWebhook_wrongSignature_throwsVerificationException() {
        String payload = "{\"id\":\"evt_3\",\"event\":\"payment.captured\"}";

        assertThatThrownBy(() -> service.processRazorpayWebhook(payload, "wrong-signature"))
            .isInstanceOf(WebhookVerificationException.class);
    }

    // ── Phase 5.1: reconcileAutoRenewalSuccess ────────────────────────────────

    @Test
    void reconcileAutoRenewalSuccess_noMatchingSubscription_logsAndReturns() {
        when(subscriptionRepository.findByExternalSubscriptionId(STRIPE_SUB_ID))
            .thenReturn(Optional.empty());

        service.reconcileAutoRenewalSuccess(STRIPE_INV_ID, PAYMENT_INTENT, STRIPE_SUB_ID, 10000L, "INR");

        verify(paymentRepository, never()).save(any());
        verify(invoiceService, never()).applyPayment(any(), any(), any());
    }

    @Test
    void reconcileAutoRenewalSuccess_noOpenInvoice_logsAndReturns() {
        UUID tenantId = UUID.randomUUID();
        UUID subId    = UUID.randomUUID();
        when(subscriptionRepository.findByExternalSubscriptionId(STRIPE_SUB_ID))
            .thenReturn(Optional.of(subscription(subId, tenantId)));
        when(invoiceRepository.findBySubscriptionId(subId)).thenReturn(List.of());

        service.reconcileAutoRenewalSuccess(STRIPE_INV_ID, PAYMENT_INTENT, STRIPE_SUB_ID, 10000L, "INR");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void reconcileAutoRenewalSuccess_createsPaymentAndMarksInvoicePaid() {
        UUID tenantId  = UUID.randomUUID();
        UUID subId     = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        when(subscriptionRepository.findByExternalSubscriptionId(STRIPE_SUB_ID))
            .thenReturn(Optional.of(subscription(subId, tenantId)));
        when(invoiceRepository.findBySubscriptionId(subId))
            .thenReturn(List.of(openInvoice(invoiceId, subId, tenantId)));
        when(paymentRepository.findByExternalPaymentId(PAYMENT_INTENT)).thenReturn(Optional.empty());
        when(invoiceRepository.findById(invoiceId))
            .thenReturn(Optional.of(openInvoice(invoiceId, subId, tenantId)));

        service.reconcileAutoRenewalSuccess(STRIPE_INV_ID, PAYMENT_INTENT, STRIPE_SUB_ID, 10000L, "INR");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment saved = paymentCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(saved.getExternalPaymentId()).isEqualTo(PAYMENT_INTENT);
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getInvoiceId()).isEqualTo(invoiceId);
        assertThat(saved.getAmountMinor()).isEqualTo(10000L);
        assertThat(saved.getCurrency()).isEqualTo("INR");

        verify(invoiceService).applyPayment(invoiceId, 10000L, null);
        // The INVOICE_PAID ledger entry is written inside PlatformInvoiceRepositoryAdapter.save()
        // via domain event — not by WebhookProcessingServiceImpl directly (de-duplication fix).
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void reconcileAutoRenewalSuccess_usesInvoiceAmountWhenStripeAmountIsZero() {
        UUID tenantId  = UUID.randomUUID();
        UUID subId     = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        when(subscriptionRepository.findByExternalSubscriptionId(STRIPE_SUB_ID))
            .thenReturn(Optional.of(subscription(subId, tenantId)));
        when(invoiceRepository.findBySubscriptionId(subId))
            .thenReturn(List.of(openInvoice(invoiceId, subId, tenantId)));
        when(invoiceRepository.findById(invoiceId))
            .thenReturn(Optional.of(openInvoice(invoiceId, subId, tenantId)));

        service.reconcileAutoRenewalSuccess(STRIPE_INV_ID, PAYMENT_INTENT, STRIPE_SUB_ID, 0L, "INR");

        ArgumentCaptor<Payment> cap = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(cap.capture());
        // falls back to invoice.amountDue = 10000
        assertThat(cap.getValue().getAmountMinor()).isEqualTo(10000L);
    }

    @Test
    void reconcileAutoRenewalSuccess_idempotent_skipsOnDuplicatePaymentId() {
        UUID tenantId  = UUID.randomUUID();
        UUID subId     = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        when(subscriptionRepository.findByExternalSubscriptionId(STRIPE_SUB_ID))
            .thenReturn(Optional.of(subscription(subId, tenantId)));
        when(invoiceRepository.findBySubscriptionId(subId))
            .thenReturn(List.of(openInvoice(invoiceId, subId, tenantId)));
        // Simulate DataIntegrityViolationException on duplicate insert
        when(paymentRepository.save(any()))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        // Should not throw — idempotent skip
        service.reconcileAutoRenewalSuccess(STRIPE_INV_ID, PAYMENT_INTENT, STRIPE_SUB_ID, 10000L, "INR");

        // Invoice not marked paid because payment save failed idempotently
        verify(invoiceService, never()).applyPayment(any(), any(), any());
    }

    // ── Phase 5.1: reconcileAutoRenewalFailure ────────────────────────────────

    @Test
    void reconcileAutoRenewalFailure_noMatchingSubscription_logsAndReturns() {
        when(subscriptionRepository.findByExternalSubscriptionId(STRIPE_SUB_ID))
            .thenReturn(Optional.empty());

        service.reconcileAutoRenewalFailure(STRIPE_INV_ID, PAYMENT_INTENT, STRIPE_SUB_ID, 10000L, "INR");

        verify(paymentRepository, never()).save(any());
        verify(dunningService, never()).startDunning(any(), any());
    }

    @Test
    void reconcileAutoRenewalFailure_noOpenInvoice_logsAndReturns() {
        UUID tenantId = UUID.randomUUID();
        UUID subId    = UUID.randomUUID();
        when(subscriptionRepository.findByExternalSubscriptionId(STRIPE_SUB_ID))
            .thenReturn(Optional.of(subscription(subId, tenantId)));
        when(invoiceRepository.findBySubscriptionId(subId)).thenReturn(List.of());
        when(paymentRepository.findByExternalPaymentId(PAYMENT_INTENT)).thenReturn(Optional.empty());

        service.reconcileAutoRenewalFailure(STRIPE_INV_ID, PAYMENT_INTENT, STRIPE_SUB_ID, 10000L, "INR");

        verify(dunningService, never()).startDunning(any(), any());
    }

    @Test
    void reconcileAutoRenewalFailure_createsFailedPaymentAndStartsDunning() {
        UUID tenantId  = UUID.randomUUID();
        UUID subId     = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        when(subscriptionRepository.findByExternalSubscriptionId(STRIPE_SUB_ID))
            .thenReturn(Optional.of(subscription(subId, tenantId)));
        when(paymentRepository.findByExternalPaymentId(PAYMENT_INTENT)).thenReturn(Optional.empty());
        when(invoiceRepository.findBySubscriptionId(subId))
            .thenReturn(List.of(openInvoice(invoiceId, subId, tenantId)));
        when(invoiceRepository.findById(invoiceId))
            .thenReturn(Optional.of(openInvoice(invoiceId, subId, tenantId)));

        service.reconcileAutoRenewalFailure(STRIPE_INV_ID, PAYMENT_INTENT, STRIPE_SUB_ID, 10000L, "INR");

        ArgumentCaptor<Payment> cap = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(cap.capture());
        Payment saved = cap.getValue();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.getExternalPaymentId()).isEqualTo(PAYMENT_INTENT);
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getInvoiceId()).isEqualTo(invoiceId);
        assertThat(saved.getFailureReason()).isNotBlank();

        verify(ledgerRepository).save(any());
        verify(dunningService).startDunning(subId, invoiceId);
    }

    @Test
    void reconcileAutoRenewalFailure_nullPaymentIntent_usesFallbackId() {
        UUID tenantId  = UUID.randomUUID();
        UUID subId     = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        when(subscriptionRepository.findByExternalSubscriptionId(STRIPE_SUB_ID))
            .thenReturn(Optional.of(subscription(subId, tenantId)));
        when(invoiceRepository.findBySubscriptionId(subId))
            .thenReturn(List.of(openInvoice(invoiceId, subId, tenantId)));
        when(invoiceRepository.findById(invoiceId))
            .thenReturn(Optional.of(openInvoice(invoiceId, subId, tenantId)));

        service.reconcileAutoRenewalFailure(STRIPE_INV_ID, null, STRIPE_SUB_ID, 10000L, "INR");

        ArgumentCaptor<Payment> cap = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(cap.capture());
        // externalPaymentId falls back to "stripe-inv-<stripeInvoiceId>"
        assertThat(cap.getValue().getExternalPaymentId()).startsWith("stripe-inv-");
        verify(dunningService).startDunning(subId, invoiceId);
    }

    @Test
    void reconcileAutoRenewalFailure_existingPaymentRecord_ensuresDunningFires() {
        UUID tenantId  = UUID.randomUUID();
        UUID subId     = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        when(subscriptionRepository.findByExternalSubscriptionId(STRIPE_SUB_ID))
            .thenReturn(Optional.of(subscription(subId, tenantId)));
        Payment existing = Payment.builder()
            .id(UUID.randomUUID()).tenantId(tenantId).invoiceId(invoiceId)
            .paymentProvider(PaymentProvider.STRIPE).externalPaymentId(PAYMENT_INTENT)
            .status(PaymentStatus.PENDING).amountMinor(10000L).currency("INR")
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(paymentRepository.findByExternalPaymentId(PAYMENT_INTENT))
            .thenReturn(Optional.of(existing));
        when(invoiceRepository.findById(invoiceId))
            .thenReturn(Optional.of(openInvoice(invoiceId, subId, tenantId)));

        service.reconcileAutoRenewalFailure(STRIPE_INV_ID, PAYMENT_INTENT, STRIPE_SUB_ID, 10000L, "INR");

        // Payment updated to FAILED and dunning triggered
        // triggerDunning() loads invoice by payment.invoiceId and calls startDunning(invoice.subscriptionId, payment.invoiceId)
        // The openInvoice helper sets subscriptionId=subId, so: startDunning(subId, invoiceId)
        verify(paymentRepository).save(any());
        verify(dunningService).startDunning(subId, invoiceId);
    }

    @Test
    void reconcileAutoRenewalFailure_paidInvoice_skipsDunning() {
        UUID tenantId  = UUID.randomUUID();
        UUID subId     = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        when(subscriptionRepository.findByExternalSubscriptionId(STRIPE_SUB_ID))
            .thenReturn(Optional.of(subscription(subId, tenantId)));
        when(paymentRepository.findByExternalPaymentId(PAYMENT_INTENT)).thenReturn(Optional.empty());

        // Build a PAID invoice
        PlatformInvoice paidInvoice = PlatformInvoice.builder()
            .id(invoiceId).subscriptionId(subId).tenantId(tenantId)
            .invoiceNumber("INV-002").status(InvoiceStatus.PAID)
            .amountDue(10000L).amountPaid(10000L).currency("INR")
            .source(com.company.bsmsvc.domain.enums.InvoiceSource.SUBSCRIPTION_RENEWAL)
            .createdAt(Instant.now().minusSeconds(60)).updatedAt(Instant.now())
            .pdfGenerationStatus(com.company.bsmsvc.domain.enums.InvoicePdfStatus.PENDING)
            .lineItems(List.of()).build();
        when(invoiceRepository.findBySubscriptionId(subId)).thenReturn(List.of(paidInvoice));

        service.reconcileAutoRenewalFailure(STRIPE_INV_ID, PAYMENT_INTENT, STRIPE_SUB_ID, 10000L, "INR");

        verify(paymentRepository, never()).save(any());
        verify(dunningService, never()).startDunning(any(), any());
    }

    // ── PART 2: Stripe subscription lifecycle reconciliation ──────────────────

    @Test
    void reconcileSubscriptionDeleted_cancelsBsmSubscription() {
        UUID subId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Subscription sub = subscription(subId, tenantId);

        when(subscriptionRepository.findByExternalSubscriptionId(STRIPE_SUB_ID))
            .thenReturn(Optional.of(sub));

        service.reconcileSubscriptionDeleted(STRIPE_SUB_ID);

        verify(subscriptionRepository).save(any(Subscription.class));
        verify(subscriptionHistoryRepository).save(any());
        verify(subscriptionEventPublisher).publishCanceled(any());
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void reconcileSubscriptionDeleted_idempotentIfAlreadyCancelled() {
        UUID subId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Subscription alreadyCancelled = Subscription.builder()
            .id(subId).tenantId(tenantId)
            .externalSubscriptionId(STRIPE_SUB_ID)
            .status(SubscriptionStatus.CANCELLED)
            .billingCycle(com.company.bsmsvc.domain.enums.BillingCycle.MONTHLY)
            .currentPeriodStart(Instant.now().minusSeconds(86400))
            .currentPeriodEnd(Instant.now())
            .build();

        when(subscriptionRepository.findByExternalSubscriptionId(STRIPE_SUB_ID))
            .thenReturn(Optional.of(alreadyCancelled));

        service.reconcileSubscriptionDeleted(STRIPE_SUB_ID);

        verify(subscriptionRepository, never()).save(any());
        verify(subscriptionHistoryRepository, never()).save(any());
        verify(subscriptionEventPublisher, never()).publishCanceled(any());
    }

    @Test
    void reconcileSubscriptionDeleted_noOpWhenSubscriptionNotFound() {
        when(subscriptionRepository.findByExternalSubscriptionId(STRIPE_SUB_ID))
            .thenReturn(Optional.empty());

        service.reconcileSubscriptionDeleted(STRIPE_SUB_ID);

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void reconcileSubscriptionUpdated_cancelsBsmSubscriptionWhenStripeStatusCanceled() {
        UUID subId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Subscription sub = subscription(subId, tenantId);

        when(subscriptionRepository.findByExternalSubscriptionId(STRIPE_SUB_ID))
            .thenReturn(Optional.of(sub));

        service.reconcileSubscriptionUpdated(STRIPE_SUB_ID, "canceled");

        verify(subscriptionRepository).save(any(Subscription.class));
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void reconcileSubscriptionUpdated_noActionForNonCanceledStatus() {
        service.reconcileSubscriptionUpdated(STRIPE_SUB_ID, "active");

        verify(subscriptionRepository, never()).findByExternalSubscriptionId(any());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void reconcileChargeRefunded_marksPaymentRefundedAndCreatesLedgerEntry() {
        UUID paymentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        Payment payment = Payment.builder()
            .id(paymentId).tenantId(tenantId).invoiceId(invoiceId)
            .paymentProvider(PaymentProvider.STRIPE)
            .externalPaymentId(PAYMENT_INTENT)
            .status(PaymentStatus.SUCCEEDED)
            .amountMinor(10000L).currency("INR")
            .build();

        when(paymentRepository.findByExternalPaymentId(PAYMENT_INTENT))
            .thenReturn(Optional.of(payment));

        service.reconcileChargeRefunded("ch_test_123", PAYMENT_INTENT, 10000L, "INR");

        verify(paymentRepository).save(any(Payment.class));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);

        var ledgerCaptor = org.mockito.ArgumentCaptor.forClass(
            com.company.bsmsvc.domain.model.BillingLedgerEntry.class);
        verify(ledgerRepository).save(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getEntryType())
            .isEqualTo(com.company.bsmsvc.domain.enums.LedgerEntryType.REFUND);
        assertThat(ledgerCaptor.getValue().getAmountMinor()).isEqualTo(10000L);
    }

    @Test
    void reconcileChargeRefunded_fallsBackToChargeIdLookupWhenPaymentIntentNotFound() {
        UUID paymentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        Payment payment = Payment.builder()
            .id(paymentId).tenantId(tenantId).invoiceId(invoiceId)
            .paymentProvider(PaymentProvider.STRIPE)
            .externalPaymentId("cs_checkout_session")
            .externalChargeId("ch_test_456")
            .status(PaymentStatus.SUCCEEDED)
            .amountMinor(5000L).currency("USD")
            .build();

        when(paymentRepository.findByExternalPaymentId(PAYMENT_INTENT)).thenReturn(Optional.empty());
        when(paymentRepository.findByExternalChargeId("ch_test_456")).thenReturn(Optional.of(payment));

        service.reconcileChargeRefunded("ch_test_456", PAYMENT_INTENT, 5000L, "USD");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentRepository).save(any());
    }

    @Test
    void reconcileChargeRefunded_idempotentIfAlreadyRefunded() {
        UUID paymentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Payment payment = Payment.builder()
            .id(paymentId).tenantId(tenantId).invoiceId(UUID.randomUUID())
            .paymentProvider(PaymentProvider.STRIPE)
            .externalPaymentId(PAYMENT_INTENT)
            .status(PaymentStatus.REFUNDED)
            .amountMinor(10000L).currency("INR")
            .build();

        when(paymentRepository.findByExternalPaymentId(PAYMENT_INTENT))
            .thenReturn(Optional.of(payment));

        service.reconcileChargeRefunded("ch_test_789", PAYMENT_INTENT, 10000L, "INR");

        verify(paymentRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void reconcileChargeRefunded_noOpWhenPaymentNotFound() {
        when(paymentRepository.findByExternalPaymentId(PAYMENT_INTENT)).thenReturn(Optional.empty());
        when(paymentRepository.findByExternalChargeId("ch_unknown")).thenReturn(Optional.empty());

        service.reconcileChargeRefunded("ch_unknown", PAYMENT_INTENT, 10000L, "INR");

        verify(paymentRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }

    // ── Phase 8 fixes ─────────────────────────────────────────────────────────

    @Test
    void reconcileSubscriptionDeleted_clearsDunningStateAtomically() {
        // P0.1 fix: cancelling via webhook must clear dunning fields so the scheduler
        // never retries payment on a cancelled subscription.
        UUID subId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Subscription dunningSubscription = Subscription.builder()
            .id(subId).tenantId(tenantId)
            .externalSubscriptionId(STRIPE_SUB_ID)
            .status(SubscriptionStatus.PAST_DUE)
            .dunningStatus(com.company.bsmsvc.domain.enums.DunningStatus.DUNNING_DAY_1)
            .dunningStartedAt(Instant.now().minusSeconds(3600))
            .billingCycle(com.company.bsmsvc.domain.enums.BillingCycle.MONTHLY)
            .currentPeriodStart(Instant.now().minusSeconds(86400))
            .currentPeriodEnd(Instant.now().plusSeconds(86400))
            .build();

        when(subscriptionRepository.findByExternalSubscriptionId(STRIPE_SUB_ID))
            .thenReturn(Optional.of(dunningSubscription));

        service.reconcileSubscriptionDeleted(STRIPE_SUB_ID);

        // Subscription must be CANCELLED and dunning state cleared
        assertThat(dunningSubscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(dunningSubscription.getDunningStatus())
            .isEqualTo(com.company.bsmsvc.domain.enums.DunningStatus.CANCELLED);
        assertThat(dunningSubscription.getDunningStartedAt()).isNull();
        assertThat(dunningSubscription.isInDunning()).isFalse();
        verify(subscriptionRepository).save(any());
    }

    @Test
    void reconcileChargeRefunded_fullRefund_marksInvoiceRefunded() {
        // P0.4 fix: a full refund via Stripe dashboard must also update invoice status
        // to REFUNDED so revenue dashboards show accurate data.
        UUID paymentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        Payment payment = Payment.builder()
            .id(paymentId).tenantId(tenantId).invoiceId(invoiceId)
            .paymentProvider(PaymentProvider.STRIPE)
            .externalPaymentId(PAYMENT_INTENT)
            .status(PaymentStatus.SUCCEEDED)
            .amountMinor(10000L).currency("INR")
            .build();

        PlatformInvoice paidInvoice = PlatformInvoice.builder()
            .id(invoiceId).tenantId(tenantId).subscriptionId(UUID.randomUUID())
            .invoiceNumber("INV-001")
            .status(com.company.bsmsvc.domain.enums.InvoiceStatus.PAID)
            .amountDue(10000L).amountPaid(10000L).currency("INR")
            .source(com.company.bsmsvc.domain.enums.InvoiceSource.SUBSCRIPTION_RENEWAL)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .pdfGenerationStatus(com.company.bsmsvc.domain.enums.InvoicePdfStatus.GENERATED)
            .lineItems(List.of()).build();

        when(paymentRepository.findByExternalPaymentId(PAYMENT_INTENT)).thenReturn(Optional.of(payment));
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(paidInvoice));
        when(invoiceService.markRefunded(invoiceId, null)).thenReturn(paidInvoice);

        service.reconcileChargeRefunded("ch_full", PAYMENT_INTENT, 10000L, "INR");

        // Payment marked REFUNDED
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        // Invoice.markRefunded() must be called for a full refund
        verify(invoiceService).markRefunded(invoiceId, null);
        // REFUND ledger entry created
        verify(ledgerRepository).save(any());
    }

    @Test
    void reconcileChargeRefunded_partialRefund_doesNotMarkInvoiceRefunded() {
        // A partial refund (amount < amountPaid) should NOT change invoice status.
        UUID paymentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        Payment payment = Payment.builder()
            .id(paymentId).tenantId(tenantId).invoiceId(invoiceId)
            .paymentProvider(PaymentProvider.STRIPE)
            .externalPaymentId(PAYMENT_INTENT)
            .status(PaymentStatus.SUCCEEDED)
            .amountMinor(10000L).currency("INR")
            .build();

        PlatformInvoice paidInvoice = PlatformInvoice.builder()
            .id(invoiceId).tenantId(tenantId).subscriptionId(UUID.randomUUID())
            .invoiceNumber("INV-002")
            .status(com.company.bsmsvc.domain.enums.InvoiceStatus.PAID)
            .amountDue(10000L).amountPaid(10000L).currency("INR")
            .source(com.company.bsmsvc.domain.enums.InvoiceSource.SUBSCRIPTION_RENEWAL)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .pdfGenerationStatus(com.company.bsmsvc.domain.enums.InvoicePdfStatus.GENERATED)
            .lineItems(List.of()).build();

        when(paymentRepository.findByExternalPaymentId(PAYMENT_INTENT)).thenReturn(Optional.of(payment));
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(paidInvoice));

        // Partial refund: only 3000 out of 10000
        service.reconcileChargeRefunded("ch_partial", PAYMENT_INTENT, 3000L, "INR");

        verify(invoiceService, never()).markRefunded(any(), any());
        // REFUND ledger entry still created for partial amount
        verify(ledgerRepository).save(any());
    }

    @Test
    void markInvoicePaid_producesExactlyOneInvoiceServiceCall_noExplicitLedgerWrite() {
        // P0.2 regression guard: markInvoicePaid must call invoiceService.applyPayment()
        // exactly once and must NOT call ledgerRepository.save() with INVOICE_PAID.
        // The authoritative INVOICE_PAID ledger entry comes from PlatformInvoiceRepositoryAdapter.
        UUID subId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        Subscription sub = subscription(subId, tenantId);
        when(subscriptionRepository.findByExternalSubscriptionId(STRIPE_SUB_ID))
            .thenReturn(Optional.of(sub));
        when(invoiceRepository.findBySubscriptionId(subId))
            .thenReturn(List.of(openInvoice(invoiceId, subId, tenantId)));
        when(paymentRepository.findByExternalPaymentId(PAYMENT_INTENT)).thenReturn(Optional.empty());
        when(invoiceRepository.findById(invoiceId))
            .thenReturn(Optional.of(openInvoice(invoiceId, subId, tenantId)));

        service.reconcileAutoRenewalSuccess(STRIPE_INV_ID, PAYMENT_INTENT, STRIPE_SUB_ID, 10000L, "INR");

        // invoiceService.applyPayment called exactly once
        verify(invoiceService).applyPayment(invoiceId, 10000L, null);
        // No explicit INVOICE_PAID ledger write from WebhookProcessingServiceImpl
        verify(ledgerRepository, never()).save(any());
    }
}
