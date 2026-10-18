package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.company.bsmsvc.domain.model.BillingLedgerEntry;

import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.domain.port.DunningEventPublisher;
import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.model.DunningPolicy;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.DunningAttemptStatus;
import com.company.bsmsvc.domain.enums.DunningStatus;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.enums.PaymentStatus;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.enums.InvoiceStatus;
import com.company.bsmsvc.domain.model.DunningAttempt;
import com.company.bsmsvc.domain.model.Payment;
import com.company.bsmsvc.domain.model.PaymentMethod;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.model.payment.PaymentIntentResult;
import com.company.bsmsvc.domain.port.BillingLedgerRepositoryPort;
import com.company.bsmsvc.domain.port.DunningAttemptRepositoryPort;
import com.company.bsmsvc.domain.port.PaymentGatewayPort;
import com.company.bsmsvc.domain.port.PaymentMethodRepositoryPort;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DunningServiceImplTest {

    @Mock private DunningAttemptRepositoryPort dunningAttemptRepository;
    @Mock private SubscriptionRepositoryPort subscriptionRepository;
    @Mock private SubscriptionHistoryRepositoryPort subscriptionHistoryRepository;
    @Mock private PaymentMethodRepositoryPort paymentMethodRepository;
    @Mock private PaymentRepositoryPort paymentRepository;
    @Mock private BillingLedgerRepositoryPort ledgerRepository;
    @Mock private TenantBillingProfileService billingProfileService;
    @Mock private PaymentGatewayResolver resolver;
    @Mock private InvoiceService invoiceService;
    private final DunningPolicy dunningPolicy = new DunningPolicy(24, 72, 168, 14, 30);
    @Mock private PaymentGatewayPort gateway;
    @Mock private DunningEventPublisher dunningEventPublisher;
    @Mock private com.company.bsmsvc.domain.port.SubscriptionEventPublisherPort subscriptionEventPublisher;
    @Mock private com.company.bsmsvc.domain.port.TenantScopePort tenantScopeEnforcer;
    @Mock private com.company.bsmsvc.domain.port.EventPublisherPort auditEventPublisher;
    private DunningServiceImpl dunningService;

    private UUID tenantId;
    private UUID subscriptionId;
    private UUID invoiceId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();
        dunningService = new DunningServiceImpl(
            dunningEventPublisher, dunningAttemptRepository, subscriptionRepository,
            subscriptionHistoryRepository, paymentMethodRepository, paymentRepository,
            ledgerRepository, billingProfileService, resolver, invoiceService,
            dunningPolicy, tenantScopeEnforcer, subscriptionEventPublisher, auditEventPublisher);
        when(resolver.resolve(any())).thenReturn(gateway);
        when(dunningAttemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlatformInvoice stubInvoice = PlatformInvoice.builder()
            .id(invoiceId).tenantId(tenantId)
            .amountDue(1000L).currency("INR")
            .status(InvoiceStatus.OPEN)
            .periodStart(Instant.now()).periodEnd(Instant.now().plusSeconds(86400))
            .dueDate(LocalDate.now().plusDays(15))
            .build();
        when(invoiceService.getInvoiceById(any())).thenReturn(stubInvoice);
    }

    @Test
    void startDunning_createsAttemptAndMarksPastDue() {
        Subscription sub = activeSubscription();
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(sub));

        dunningService.startDunning(subscriptionId, invoiceId);

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(sub.getDunningStatus()).isEqualTo(DunningStatus.DUNNING_DAY_1);
        verify(dunningAttemptRepository).save(any(DunningAttempt.class));
        verify(subscriptionRepository).save(sub);
        // NEW audit-only leg: bsm.dunning.started, additive to the existing direct-RabbitTemplate
        // business publish (dunningEventPublisher.publishStarted) which stays untouched.
        verify(auditEventPublisher).publish(
            org.mockito.ArgumentMatchers.eq("bsm.dunning.started"),
            org.mockito.ArgumentMatchers.eq(tenantId),
            org.mockito.ArgumentMatchers.eq("DunningAttempt"),
            any(), org.mockito.Mockito.isNull(), any());
        verify(dunningEventPublisher).publishStarted(subscriptionId, tenantId, invoiceId, 1);
    }

    @Test
    void startDunning_isIdempotent_whenAlreadyInDunning() {
        Subscription sub = activeSubscription();
        sub.startDunning(invoiceId, Instant.now().plusSeconds(86400));
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(sub));

        dunningService.startDunning(subscriptionId, invoiceId);

        verify(dunningAttemptRepository, never()).save(any(DunningAttempt.class));
    }

    @Test
    void processDueAttempts_retrySucceeds_recoversSubscription() {
        Subscription sub = dunningSubscription(DunningStatus.DUNNING_DAY_1);
        DunningAttempt attempt = pendingAttempt(1);
        TenantBillingProfile profile = profile();
        PaymentMethod pm = paymentMethod();

        when(dunningAttemptRepository.findDuePending(any())).thenReturn(List.of(attempt));
        when(subscriptionRepository.findById(attempt.getSubscriptionId())).thenReturn(Optional.of(sub));
        when(paymentMethodRepository.findDefaultByTenantId(tenantId)).thenReturn(Optional.of(pm));
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(gateway.retryPayment(any())).thenReturn(new PaymentIntentResult("pi_test", null, "succeeded"));
        when(paymentRepository.findByInvoiceId(invoiceId)).thenReturn(List.of());

        dunningService.processDueAttempts();

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getDunningStatus()).isEqualTo(DunningStatus.NORMAL);
        verify(auditEventPublisher).publish(
            org.mockito.ArgumentMatchers.eq("bsm.dunning.recovered"),
            org.mockito.ArgumentMatchers.eq(tenantId),
            org.mockito.ArgumentMatchers.eq("DunningAttempt"),
            any(), org.mockito.Mockito.isNull(), any());
    }

    @Test
    void processDueAttempts_retryFails_advancesToDunningDay3() {
        Subscription sub = dunningSubscription(DunningStatus.DUNNING_DAY_1);
        DunningAttempt attempt = pendingAttempt(1);
        TenantBillingProfile profile = profile();
        PaymentMethod pm = paymentMethod();

        when(dunningAttemptRepository.findDuePending(any())).thenReturn(List.of(attempt));
        when(subscriptionRepository.findById(attempt.getSubscriptionId())).thenReturn(Optional.of(sub));
        when(paymentMethodRepository.findDefaultByTenantId(tenantId)).thenReturn(Optional.of(pm));
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(gateway.retryPayment(any())).thenThrow(new com.company.bsmsvc.domain.exception.PaymentGatewayException("Card declined"));

        dunningService.processDueAttempts();

        assertThat(sub.getDunningStatus()).isEqualTo(DunningStatus.DUNNING_DAY_3);
        verify(auditEventPublisher).publish(
            org.mockito.ArgumentMatchers.eq("bsm.dunning.retry"),
            org.mockito.ArgumentMatchers.eq(tenantId),
            org.mockito.ArgumentMatchers.eq("DunningAttempt"),
            any(), org.mockito.Mockito.isNull(), any());
    }

    @Test
    void processDueAttempts_noDefaultPM_advancesDunning() {
        Subscription sub = dunningSubscription(DunningStatus.DUNNING_DAY_1);
        DunningAttempt attempt = pendingAttempt(1);

        when(dunningAttemptRepository.findDuePending(any())).thenReturn(List.of(attempt));
        when(subscriptionRepository.findById(attempt.getSubscriptionId())).thenReturn(Optional.of(sub));
        when(paymentMethodRepository.findDefaultByTenantId(tenantId)).thenReturn(Optional.empty());

        dunningService.processDueAttempts();

        assertThat(sub.getDunningStatus()).isEqualTo(DunningStatus.DUNNING_DAY_3);
        verify(gateway, never()).retryPayment(any());
    }

    @Test
    void processDueAttempts_day7Failure_suspends() {
        Subscription sub = dunningSubscription(DunningStatus.DUNNING_DAY_7);
        DunningAttempt attempt = pendingAttempt(3);
        TenantBillingProfile profile = profile();
        PaymentMethod pm = paymentMethod();

        when(dunningAttemptRepository.findDuePending(any())).thenReturn(List.of(attempt));
        when(subscriptionRepository.findById(attempt.getSubscriptionId())).thenReturn(Optional.of(sub));
        when(paymentMethodRepository.findDefaultByTenantId(tenantId)).thenReturn(Optional.of(pm));
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(gateway.retryPayment(any())).thenThrow(new com.company.bsmsvc.domain.exception.PaymentGatewayException("Declined"));

        dunningService.processDueAttempts();

        assertThat(sub.getDunningStatus()).isEqualTo(DunningStatus.SUSPENDED_PENDING_PURGE);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED_PENDING_PURGE);
        verify(auditEventPublisher).publish(
            org.mockito.ArgumentMatchers.eq("bsm.dunning.suspended"),
            org.mockito.ArgumentMatchers.eq(tenantId),
            org.mockito.ArgumentMatchers.eq("DunningAttempt"),
            any(), org.mockito.Mockito.isNull(), any());
    }

    @Test
    void processDueAttempts_suspendedAttempt_cancels() {
        Subscription sub = dunningSubscription(DunningStatus.SUSPENDED_PENDING_PURGE);
        sub.advanceDunningAfterFailure(DunningStatus.SUSPENDED_PENDING_PURGE, Instant.now());
        DunningAttempt attempt = pendingAttempt(4);

        when(dunningAttemptRepository.findDuePending(any())).thenReturn(List.of(attempt));
        when(subscriptionRepository.findById(attempt.getSubscriptionId())).thenReturn(Optional.of(sub));
        when(paymentMethodRepository.findDefaultByTenantId(tenantId)).thenReturn(Optional.empty());

        dunningService.processDueAttempts();

        assertThat(sub.getDunningStatus()).isEqualTo(DunningStatus.CANCELLED);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        // NOTE: this scenario (subscription already SUSPENDED_PENDING_PURGE on entry) takes
        // executeAttempt's early-return cancellation branch, which never calls
        // dunningEventPublisher.publishCancelled nor the new bsm.dunning.cancelled audit leg —
        // both live only in advanceDunningAfterFailure's CANCELLED branch, which the guard above
        // makes effectively unreachable in the current state machine (reported, not asserted here).
    }

    private Subscription activeSubscription() {
        return Subscription.builder()
            .id(subscriptionId).tenantId(tenantId).planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.ACTIVE).billingCycle(BillingCycle.MONTHLY)
            .dunningStatus(DunningStatus.NORMAL)
            .currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now().plusSeconds(86400))
            .domainEvents(new ArrayList<>()).build();
    }

    private Subscription dunningSubscription(DunningStatus ds) {
        return Subscription.builder()
            .id(subscriptionId).tenantId(tenantId).planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.PAST_DUE).billingCycle(BillingCycle.MONTHLY)
            .dunningStatus(ds).dunningStartedAt(Instant.now().minusSeconds(3600))
            .currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now().plusSeconds(86400))
            .domainEvents(new ArrayList<>()).build();
    }

    private DunningAttempt pendingAttempt(int num) {
        return DunningAttempt.builder()
            .id(UUID.randomUUID()).subscriptionId(subscriptionId).tenantId(tenantId).invoiceId(invoiceId)
            .attemptNumber(num).status(DunningAttemptStatus.PENDING)
            .nextRetryAt(Instant.now().minusSeconds(60)).createdAt(Instant.now()).build();
    }

    private TenantBillingProfile profile() {
        return TenantBillingProfile.builder()
            .id(UUID.randomUUID()).tenantId(tenantId).paymentProvider(PaymentProvider.STRIPE)
            .externalCustomerId("cus_test").createdAt(Instant.now()).updatedAt(Instant.now())
            .domainEvents(new ArrayList<>()).build();
    }

    private PaymentMethod paymentMethod() {
        return PaymentMethod.builder()
            .id(UUID.randomUUID()).tenantId(tenantId).paymentProvider(PaymentProvider.STRIPE)
            .externalPaymentMethodId("pm_test").type(com.company.bsmsvc.domain.enums.PaymentMethodType.CARD)
            .isDefault(true).status(com.company.bsmsvc.domain.enums.PaymentMethodStatus.ACTIVE)
            .createdAt(Instant.now()).updatedAt(Instant.now()).domainEvents(new ArrayList<>()).build();
    }

    // ── Phase 5.2: publishChanged after dunning recovery ──────────────────────

    @Test
    void recoveryPaymentReceived_subscriptionInDunning_recoversAndPublishesEvents() {
        Subscription sub = dunningSubscription(DunningStatus.DUNNING_DAY_1);
        DunningAttempt attempt = pendingAttempt(1);
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(sub));
        when(dunningAttemptRepository.findLatestBySubscriptionId(subscriptionId))
            .thenReturn(Optional.of(attempt));
        when(dunningAttemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dunningService.recoveryPaymentReceived(subscriptionId, invoiceId);

        verify(dunningAttemptRepository).save(any());  // attempt marked SUCCEEDED
        verify(subscriptionRepository).save(any());     // subscription saved (ACTIVE, NORMAL)
        verify(dunningEventPublisher).publishRecovered(subscriptionId, tenantId, invoiceId);
        verify(subscriptionEventPublisher).publishChanged(any(), any(), any());  // TNT sync event
        // Second real call site for bsm.dunning.recovered (external-payment recovery path)
        verify(auditEventPublisher).publish(
            org.mockito.ArgumentMatchers.eq("bsm.dunning.recovered"),
            org.mockito.ArgumentMatchers.eq(tenantId),
            org.mockito.ArgumentMatchers.eq("DunningAttempt"),
            any(), org.mockito.Mockito.isNull(), any());
    }

    @Test
    void recoveryPaymentReceived_subscriptionNotInDunning_isNoOp() {
        Subscription activeSub = Subscription.builder()
            .id(subscriptionId).tenantId(tenantId).planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.ACTIVE).billingCycle(BillingCycle.MONTHLY)
            .dunningStatus(DunningStatus.NORMAL)
            .currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now().plusSeconds(86400))
            .domainEvents(new ArrayList<>()).build();
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(activeSub));

        dunningService.recoveryPaymentReceived(subscriptionId, invoiceId);

        verify(subscriptionRepository, never()).save(any());
        verify(dunningEventPublisher, never()).publishRecovered(any(), any(), any());
        verify(subscriptionEventPublisher, never()).publishChanged(any(), any(), any());
    }

    @Test
    void recoveryPaymentReceived_subscriptionNotFound_isNoOp() {
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.empty());

        dunningService.recoveryPaymentReceived(subscriptionId, invoiceId);

        verify(subscriptionRepository, never()).save(any());
        verify(dunningEventPublisher, never()).publishRecovered(any(), any(), any());
    }

    // ── Phase 8 fixes ─────────────────────────────────────────────────────────

    @Test
    void executeAttempt_terminatesImmediately_whenSubscriptionIsCancelled() {
        // Reproduce the dunning-reactivation bug scenario:
        // customer.subscription.deleted fired → sub is CANCELLED in DB but dunningStatus
        // was not cleared → DunningScheduler picks up a still-PENDING attempt.
        // The fix: executeAttempt() must detect CANCELLED status and mark the attempt
        // terminal WITHOUT calling the payment gateway.
        Subscription cancelled = Subscription.builder()
            .id(subscriptionId).tenantId(tenantId).planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.CANCELLED).billingCycle(BillingCycle.MONTHLY)
            .dunningStatus(DunningStatus.DUNNING_DAY_1)
            .currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now().plusSeconds(86400))
            .domainEvents(new ArrayList<>()).build();

        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(cancelled));

        dunningService.executeAttempt(pendingAttempt(1));

        // Gateway must NEVER be called for a cancelled subscription
        verify(gateway, never()).retryPayment(any());
        // Attempt must be marked terminal (SUCCEEDED = "resolved without retry")
        org.mockito.ArgumentCaptor<DunningAttempt> cap = org.mockito.ArgumentCaptor.forClass(DunningAttempt.class);
        verify(dunningAttemptRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(DunningAttemptStatus.SUCCEEDED);
        assertThat(cap.getValue().getFailureMessage()).contains("CANCELLED");
    }

    @Test
    void cancelAndClearDunning_preventsRecoverFromDunningFromReactivating() {
        // Verifies the Subscription domain method that closes the reactivation path.
        Subscription sub = dunningSubscription(DunningStatus.DUNNING_DAY_1);
        assertThat(sub.isInDunning()).isTrue();

        sub.cancelAndClearDunning(java.time.Instant.now());

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(sub.getDunningStatus()).isEqualTo(DunningStatus.CANCELLED);
        assertThat(sub.getDunningStartedAt()).isNull();
        assertThat(sub.getDunningNextActionAt()).isNull();
        assertThat(sub.isInDunning()).isFalse();
    }

    @Test
    void cancelAndClearDunning_idempotentWhenAlreadyCancelled() {
        Subscription sub = dunningSubscription(DunningStatus.DUNNING_DAY_3)
            .toBuilder().status(SubscriptionStatus.CANCELLED).build();

        // Should not throw
        sub.cancelAndClearDunning(java.time.Instant.now());

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(sub.isInDunning()).isFalse();
    }

    @Test
    void handleRetrySuccess_doesNotWriteExplicitInvoicePaidLedgerEntry() {
        // After P0.2 fix: handleRetrySuccess must not write a second INVOICE_PAID entry.
        // The authoritative write happens inside PlatformInvoiceRepositoryAdapter.save()
        // via the InvoiceMarkedPaidEvent domain event.
        Subscription sub = dunningSubscription(DunningStatus.DUNNING_DAY_1);
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(sub));

        var attempt = pendingAttempt(1).toBuilder().status(DunningAttemptStatus.IN_PROGRESS).build();
        when(dunningAttemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));

        var profile = TenantBillingProfile.builder().id(UUID.randomUUID()).tenantId(tenantId)
            .paymentProvider(PaymentProvider.STRIPE).externalCustomerId("cus_test")
            .currency("INR").build();
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);

        var pm = PaymentMethod.builder().id(UUID.randomUUID()).tenantId(tenantId)
            .externalPaymentMethodId("pm_test").isDefault(true).build();
        when(paymentMethodRepository.findDefaultByTenantId(tenantId)).thenReturn(Optional.of(pm));
        when(paymentRepository.findByInvoiceId(invoiceId)).thenReturn(List.of());
        when(invoiceService.applyPayment(any(), any(), any())).thenAnswer(inv -> null);
        when(gateway.retryPayment(any())).thenReturn(
            new PaymentIntentResult("pi_test", null, "succeeded"));

        dunningService.executeAttempt(attempt);

        // Only PAYMENT_FAILED-type entries should be written by DunningServiceImpl.
        // INVOICE_PAID must NOT be written here.
        verify(ledgerRepository, never()).save(argThat(e ->
            e instanceof BillingLedgerEntry l
            && l.getEntryType() == com.company.bsmsvc.domain.enums.LedgerEntryType.INVOICE_PAID));
    }

    @Test
    void advanceDunningAfterFailure_writesPaymentFailedLedgerEntry() {
        // After P1.7 fix: every dunning retry failure must produce a PAYMENT_FAILED ledger entry.
        Subscription sub = dunningSubscription(DunningStatus.DUNNING_DAY_1);
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(sub));

        var attempt = pendingAttempt(1).toBuilder().status(DunningAttemptStatus.IN_PROGRESS).build();
        when(dunningAttemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));

        var profile = TenantBillingProfile.builder().id(UUID.randomUUID()).tenantId(tenantId)
            .paymentProvider(PaymentProvider.STRIPE).externalCustomerId("cus_test").currency("INR").build();
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);

        var pm = PaymentMethod.builder().id(UUID.randomUUID()).tenantId(tenantId)
            .externalPaymentMethodId("pm_test").isDefault(true).build();
        when(paymentMethodRepository.findDefaultByTenantId(tenantId)).thenReturn(Optional.of(pm));
        when(paymentRepository.findByInvoiceId(invoiceId)).thenReturn(List.of());
        when(gateway.retryPayment(any())).thenThrow(
            new com.company.bsmsvc.domain.exception.PaymentGatewayException("card declined"));

        dunningService.executeAttempt(attempt);

        // PAYMENT_FAILED ledger entry must be written
        verify(ledgerRepository).save(argThat(e ->
            e instanceof BillingLedgerEntry l
            && l.getEntryType() == com.company.bsmsvc.domain.enums.LedgerEntryType.PAYMENT_FAILED));
    }
}
