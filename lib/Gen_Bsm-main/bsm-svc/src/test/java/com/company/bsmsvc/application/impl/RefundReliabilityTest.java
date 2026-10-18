package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.application.service.CreditNoteService;
import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.CreditNoteStatus;
import com.company.bsmsvc.domain.enums.InvoiceStatus;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.enums.PaymentStatus;
import com.company.bsmsvc.domain.enums.RefundStatus;
import com.company.bsmsvc.domain.exception.PaymentGatewayException;
import com.company.bsmsvc.domain.model.CreditNote;
import com.company.bsmsvc.domain.model.CreditNoteFilter;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.Payment;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.RefundRequest;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.model.payment.RefundCommand;
import com.company.bsmsvc.domain.model.payment.RefundResult;
import com.company.bsmsvc.domain.port.PaymentGatewayPort;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import com.company.bsmsvc.domain.port.RefundRequestRepositoryPort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.company.bsmsvc.domain.port.TenantScopePort;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.lenient;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundReliabilityTest {

    @Mock private PlatformInvoiceRepositoryPort invoiceRepository;
    @Mock private PaymentRepositoryPort paymentRepository;
    @Mock private TenantBillingProfileService billingProfileService;
    @Mock private PaymentGatewayResolver resolver;
    @Mock private CreditNoteService creditNoteService;
    @Mock private RefundRequestRepositoryPort refundRequestRepository;
    @Mock private PaymentGatewayPort gateway;
    @Mock private InvoiceService invoiceService;
    @Mock private TenantScopePort tenantScopeEnforcer;
    @Mock private com.company.bsmsvc.domain.port.EventPublisherPort auditEventPublisher;

    @InjectMocks private RefundServiceImpl refundService;

    private UUID tenantId;
    private UUID invoiceId;
    private UUID paymentId;
    private UUID creditNoteId;

    @BeforeEach
    void setUp() {
        lenient().when(tenantScopeEnforcer.resolveEffectiveTenantId(any())).thenAnswer(inv -> inv.getArgument(0));
        tenantId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        creditNoteId = UUID.randomUUID();

        Map<UUID, RefundRequest> refundStore = new HashMap<>();
        when(resolver.resolve(any())).thenReturn(gateway);
        when(refundRequestRepository.save(any())).thenAnswer(inv -> {
            RefundRequest r = inv.getArgument(0);
            refundStore.put(r.getId(), r);
            return r;
        });
        when(refundRequestRepository.findById(any())).thenAnswer(inv ->
            Optional.ofNullable(refundStore.get((UUID) inv.getArgument(0))));
        when(refundRequestRepository.findActiveByInvoiceAndPaymentAndAmount(any(), any(), any(long.class))).thenReturn(Optional.empty());
        when(refundRequestRepository.sumProviderCommittedAmountByInvoiceId(any())).thenReturn(0L);
        when(creditNoteService.listCreditNotes(any(CreditNoteFilter.class), any(int.class), any(int.class), any(), any()))
            .thenReturn(new PageResult<>(List.of(), 0, 1000, 0, 0, false));
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile());
        when(invoiceService.markRefunded(any(), any())).thenAnswer(inv -> paidInvoice(500000L));
    }

    // ── Test 1: Provider success + credit note success ────────────────────────

    @Test
    void happyPath_providerSucceedsAndCreditNoteSucceeds() {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(paidInvoice(500000L)));
        when(paymentRepository.findByInvoiceId(invoiceId)).thenReturn(List.of(succeededPayment()));
        when(gateway.refund(any(RefundCommand.class))).thenReturn(new RefundResult("re_test", "succeeded"));
        CreditNote cn = creditNote(creditNoteId, CreditNoteStatus.OPEN);
        when(creditNoteService.createCreditNote(any(), any(), any(long.class), any(), any(), any())).thenReturn(cn);
        when(creditNoteService.applyCreditNote(creditNoteId)).thenReturn(cn);

        CreditNote result = refundService.createRefund(tenantId, invoiceId, 500000L, "reason", tenantId);

        assertThat(result).isNotNull();
        // Provider called exactly once
        verify(gateway).refund(any());
        // RefundRequest saved: PENDING → creditNoteId attached → COMPLETED
        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(refundRequestRepository, org.mockito.Mockito.atLeast(3)).save(captor.capture());
        var saves = captor.getAllValues();
        assertThat(saves.get(0).getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(saves.stream().anyMatch(r -> r.getStatus() == RefundStatus.PROVIDER_REFUND_SUCCEEDED)).isTrue();
        assertThat(saves.stream().anyMatch(r -> r.getStatus() == RefundStatus.COMPLETED)).isTrue();

        // NEW audit legs: refund.created (with actor) enqueued at step 1, refund.completed
        // (with actor) enqueued when local work finishes — both via the transactional outbox.
        verify(auditEventPublisher).publish(org.mockito.Mockito.eq("refund.created"),
            org.mockito.Mockito.eq(tenantId), org.mockito.Mockito.eq("RefundRequest"), any(), org.mockito.Mockito.eq(tenantId), any());
        verify(auditEventPublisher).publish(org.mockito.Mockito.eq("refund.completed"),
            any(), org.mockito.Mockito.eq("RefundRequest"), any(), org.mockito.Mockito.eq(tenantId), any());
    }

    // ── Test 2: Provider success + credit note failure ────────────────────────

    @Test
    void providerSucceeds_creditNoteCreateFails_marksRecoveryRequired() {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(paidInvoice(500000L)));
        when(paymentRepository.findByInvoiceId(invoiceId)).thenReturn(List.of(succeededPayment()));
        when(gateway.refund(any())).thenReturn(new RefundResult("re_test_123", "succeeded"));
        when(creditNoteService.createCreditNote(any(), any(), any(long.class), any(), any(), any()))
            .thenThrow(new RuntimeException("DB unavailable"));

        assertThatThrownBy(() -> refundService.createRefund(tenantId, invoiceId, 500000L, "reason", tenantId))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Recovery is automatic");

        // RefundRequest must be RECOVERY_REQUIRED — providerRefundId must be persisted
        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(refundRequestRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        var recoveryRequest = captor.getAllValues().stream()
            .filter(r -> r.getStatus() == RefundStatus.RECOVERY_REQUIRED)
            .findFirst();
        assertThat(recoveryRequest).isPresent();
        assertThat(recoveryRequest.get().getProviderRefundId()).isEqualTo("re_test_123");
    }

    // ── Test 2b: Provider call itself fails → refund.failed audit leg ─────────

    @Test
    void providerCallFails_marksFailedAndEnqueuesRefundFailedAudit() {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(paidInvoice(500000L)));
        when(paymentRepository.findByInvoiceId(invoiceId)).thenReturn(List.of(succeededPayment()));
        when(gateway.refund(any())).thenThrow(new RuntimeException("gateway down"));

        assertThatThrownBy(() -> refundService.createRefund(tenantId, invoiceId, 500000L, "reason", tenantId))
            .isInstanceOf(com.company.bsmsvc.domain.exception.PaymentGatewayException.class);

        verify(auditEventPublisher).publish(org.mockito.Mockito.eq("refund.failed"),
            org.mockito.Mockito.eq(tenantId), org.mockito.Mockito.eq("RefundRequest"), any(),
            org.mockito.Mockito.eq(tenantId), any());
    }

    // ── Test 3: Recovery job completes missing records ────────────────────────

    @Test
    void recoveryJob_completesMissingCreditNote() {
        RefundRequest rr = RefundRequest.builder()
            .id(UUID.randomUUID()).tenantId(tenantId).invoiceId(invoiceId).paymentId(paymentId)
            .requestedAmountMinor(500000L).providerRefundId("re_abc")
            .status(RefundStatus.RECOVERY_REQUIRED).createdAt(Instant.now()).updatedAt(Instant.now()).build();

        RefundRecoveryServiceImpl recovery = new RefundRecoveryServiceImpl(
            refundRequestRepository, invoiceRepository, paymentRepository, creditNoteService, invoiceService,
            auditEventPublisher);

        when(refundRequestRepository.findById(rr.getId())).thenReturn(Optional.of(rr));
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(paidInvoice(500000L)));
        CreditNote cn = creditNote(creditNoteId, CreditNoteStatus.OPEN);
        when(creditNoteService.createCreditNote(any(), any(), any(long.class), any(), any(), any())).thenReturn(cn);
        when(creditNoteService.applyCreditNote(creditNoteId)).thenReturn(cn);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(succeededPayment()));

        recovery.recover(rr.getId());

        // Credit note created + applied
        verify(creditNoteService).createCreditNote(any(), any(), any(long.class), any(), any(), any());
        verify(creditNoteService).applyCreditNote(creditNoteId);
        // Payment marked refunded
        verify(paymentRepository).save(any());
        // RefundRequest marked COMPLETED
        ArgumentCaptor<RefundRequest> cap = ArgumentCaptor.forClass(RefundRequest.class);
        verify(refundRequestRepository, org.mockito.Mockito.atLeast(1)).save(cap.capture());
        assertThat(cap.getAllValues().stream().anyMatch(r -> r.getStatus() == RefundStatus.COMPLETED)).isTrue();

        // Automated recovery path has no actor — audit leg carries a null actor, not fabricated.
        verify(auditEventPublisher).publish(org.mockito.Mockito.eq("refund.completed"),
            any(), org.mockito.Mockito.eq("RefundRequest"), any(), org.mockito.Mockito.isNull(), any());
    }

    // ── Test 4: Duplicate refund request does not call provider twice ──────────

    @Test
    void duplicateRequest_existingPending_throwsConflict() {
        RefundRequest existing = RefundRequest.builder()
            .id(UUID.randomUUID()).tenantId(tenantId).invoiceId(invoiceId).paymentId(paymentId)
            .requestedAmountMinor(500000L).status(RefundStatus.PENDING)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(paidInvoice(500000L)));
        when(paymentRepository.findByInvoiceId(invoiceId)).thenReturn(List.of(succeededPayment()));
        when(refundRequestRepository.findActiveByInvoiceAndPaymentAndAmount(invoiceId, paymentId, 500000L))
            .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> refundService.createRefund(tenantId, invoiceId, 500000L, "reason", tenantId))
            .isInstanceOf(com.company.bsmsvc.domain.exception.BusinessRuleViolationException.class)
            .hasMessageContaining("already in progress");

        // Provider NEVER called
        verify(gateway, never()).refund(any());
    }

    // ── Test 5: Existing COMPLETED refund returns existing result ────────────

    @Test
    void duplicateRequest_existingCompleted_returnsCreditNoteWithoutProviderCall() {
        CreditNote existingCn = creditNote(creditNoteId, CreditNoteStatus.APPLIED);
        RefundRequest existing = RefundRequest.builder()
            .id(UUID.randomUUID()).tenantId(tenantId).invoiceId(invoiceId).paymentId(paymentId)
            .requestedAmountMinor(500000L).providerRefundId("re_already").creditNoteId(creditNoteId)
            .status(RefundStatus.COMPLETED).createdAt(Instant.now()).updatedAt(Instant.now()).build();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(paidInvoice(500000L)));
        when(paymentRepository.findByInvoiceId(invoiceId)).thenReturn(List.of(succeededPayment()));
        when(refundRequestRepository.findActiveByInvoiceAndPaymentAndAmount(invoiceId, paymentId, 500000L))
            .thenReturn(Optional.of(existing));
        when(creditNoteService.getCreditNoteById(creditNoteId)).thenReturn(existingCn);

        CreditNote result = refundService.createRefund(tenantId, invoiceId, 500000L, "reason", tenantId);

        assertThat(result.getId()).isEqualTo(creditNoteId);
        verify(gateway, never()).refund(any());
        verify(creditNoteService, never()).createCreditNote(any(), any(), any(long.class), any(), any(), any());
    }

    // ── Test 6: Existing RECOVERY_REQUIRED resumes from local work only ───────

    @Test
    void duplicateRequest_existingRecoveryRequired_completesLocalWorkWithoutProviderCall() {
        CreditNote cn = creditNote(creditNoteId, CreditNoteStatus.OPEN);
        RefundRequest existing = RefundRequest.builder()
            .id(UUID.randomUUID()).tenantId(tenantId).invoiceId(invoiceId).paymentId(paymentId)
            .requestedAmountMinor(500000L).providerRefundId("re_recovery_123")
            .status(RefundStatus.RECOVERY_REQUIRED).createdAt(Instant.now()).updatedAt(Instant.now()).build();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(paidInvoice(500000L)));
        when(paymentRepository.findByInvoiceId(invoiceId)).thenReturn(List.of(succeededPayment()));
        when(refundRequestRepository.findActiveByInvoiceAndPaymentAndAmount(invoiceId, paymentId, 500000L))
            .thenReturn(Optional.of(existing));
        when(creditNoteService.createCreditNote(any(), any(), any(long.class), any(), any(), any())).thenReturn(cn);
        when(creditNoteService.applyCreditNote(creditNoteId)).thenReturn(cn);

        CreditNote result = refundService.createRefund(tenantId, invoiceId, 500000L, "reason", tenantId);

        assertThat(result).isNotNull();
        // Provider NOT called again — only local work
        verify(gateway, never()).refund(any());
        verify(creditNoteService).createCreditNote(any(), any(), any(long.class), any(), any(), any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PlatformInvoice paidInvoice(long amountPaid) {
        return PlatformInvoice.builder()
            .id(invoiceId).tenantId(tenantId).subscriptionId(UUID.randomUUID())
            .invoiceNumber("INV-001").status(InvoiceStatus.PAID)
            .amountDue(amountPaid).amountPaid(amountPaid).currency("INR")
            .periodStart(Instant.now()).periodEnd(Instant.now().plusSeconds(86400))
            .dueDate(LocalDate.now().plusDays(30))
            .lineItems(new ArrayList<>()).domainEvents(new ArrayList<>()).build();
    }

    private Payment succeededPayment() {
        return Payment.builder()
            .id(paymentId).tenantId(tenantId).invoiceId(invoiceId)
            .paymentProvider(PaymentProvider.STRIPE).externalPaymentId("cs_test")
            .externalChargeId("pi_test_charge").status(PaymentStatus.SUCCEEDED)
            .amountMinor(500000L).currency("INR")
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private CreditNote creditNote(UUID id, CreditNoteStatus status) {
        return CreditNote.builder()
            .id(id).tenantId(tenantId).invoiceId(invoiceId)
            .creditNumber("CN-001").amountMinor(500000L).currency("INR")
            .reason("refund").status(status).createdBy(tenantId)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .domainEvents(new ArrayList<>()).build();
    }

    private TenantBillingProfile profile() {
        return TenantBillingProfile.builder()
            .id(UUID.randomUUID()).tenantId(tenantId)
            .paymentProvider(PaymentProvider.STRIPE).externalCustomerId("cus_test")
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .domainEvents(new ArrayList<>()).build();
    }
}
