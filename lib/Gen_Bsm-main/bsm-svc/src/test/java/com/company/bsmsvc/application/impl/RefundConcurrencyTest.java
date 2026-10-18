package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.application.service.CreditNoteService;
import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.InvoiceStatus;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.enums.PaymentStatus;
import com.company.bsmsvc.domain.enums.RefundStatus;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.Payment;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.RefundRequest;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.port.PaymentGatewayPort;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import com.company.bsmsvc.domain.port.RefundRequestRepositoryPort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.company.bsmsvc.domain.port.TenantScopePort;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.lenient;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundConcurrencyTest {

    @Mock private PlatformInvoiceRepositoryPort invoiceRepository;
    @Mock private PaymentRepositoryPort paymentRepository;
    @Mock private TenantBillingProfileService billingProfileService;
    @Mock private PaymentGatewayResolver resolver;
    @Mock private CreditNoteService creditNoteService;
    @Mock private RefundRequestRepositoryPort refundRequestRepository;

    @Mock private TenantScopePort tenantScopeEnforcer;

    @InjectMocks private RefundServiceImpl refundService;

    private UUID tenantId;
    private UUID invoiceId;
    private UUID paymentId;

    @BeforeEach
    void setUp() {
        lenient().when(tenantScopeEnforcer.resolveEffectiveTenantId(any())).thenAnswer(inv -> inv.getArgument(0));
        tenantId  = UUID.randomUUID();
        invoiceId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        // sumActiveCreditNotes calls this — stub to empty so over-refund check doesn't NPE
        when(creditNoteService.listCreditNotes(any(), anyInt(), anyInt(), any(), any()))
            .thenReturn(new PageResult<>(List.of(), 0, 1000, 0L, 0, false));
    }

    // ── Test 1: DB unique constraint violation on PENDING save → BusinessRuleViolationException ──

    @Test
    void concurrentRequest_dbConstraintViolation_raisesBusinessRuleException() {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(paidInvoice()));
        when(paymentRepository.findByInvoiceId(invoiceId)).thenReturn(List.of(succeededPayment()));
        when(refundRequestRepository.findActiveByInvoiceAndPaymentAndAmount(any(), any(), anyLong()))
            .thenReturn(Optional.empty()); // passes app-level check

        when(billingProfileService.getProfile(tenantId)).thenReturn(profile());
        when(refundRequestRepository.sumProviderCommittedAmountByInvoiceId(invoiceId)).thenReturn(0L);
        // DB unique index fires — simulates race where two threads both passed findActive
        when(refundRequestRepository.save(any(RefundRequest.class)))
            .thenThrow(new DataIntegrityViolationException("unique constraint violation"));

        assertThatThrownBy(() -> refundService.createRefund(tenantId, invoiceId, 500L, "test", tenantId))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("concurrent request detected");

        // Provider must never be called
        verify(resolver, never()).resolve(any());
    }

    // ── Test 2: Second sequential request with existing active record → blocked ──

    @Test
    void existingActiveRequest_blocksSecondProviderCall() {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(paidInvoice()));
        when(paymentRepository.findByInvoiceId(invoiceId)).thenReturn(List.of(succeededPayment()));
        when(refundRequestRepository.sumProviderCommittedAmountByInvoiceId(invoiceId)).thenReturn(0L);

        RefundRequest existing = RefundRequest.builder()
            .id(UUID.randomUUID()).tenantId(tenantId).invoiceId(invoiceId).paymentId(paymentId)
            .requestedAmountMinor(500L).status(RefundStatus.PENDING)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(refundRequestRepository.findActiveByInvoiceAndPaymentAndAmount(invoiceId, paymentId, 500L))
            .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> refundService.createRefund(tenantId, invoiceId, 500L, "test", tenantId))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("already in progress");

        verify(resolver, never()).resolve(any());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private PlatformInvoice paidInvoice() {
        return PlatformInvoice.builder()
            .id(invoiceId).tenantId(tenantId).status(InvoiceStatus.PAID)
            .amountDue(1000L).amountPaid(1000L).currency("INR")
            .periodStart(Instant.now()).periodEnd(Instant.now().plusSeconds(86400))
            .dueDate(LocalDate.now().plusDays(15))
            .lineItems(new ArrayList<>()).domainEvents(new ArrayList<>()).build();
    }

    private Payment succeededPayment() {
        return Payment.builder()
            .id(paymentId).tenantId(tenantId).invoiceId(invoiceId)
            .status(PaymentStatus.SUCCEEDED).amountMinor(1000L).currency("INR")
            .externalChargeId("ch_test").externalPaymentId("pi_test")
            .paymentProvider(PaymentProvider.STRIPE)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private TenantBillingProfile profile() {
        return TenantBillingProfile.builder()
            .tenantId(tenantId).paymentProvider(PaymentProvider.STRIPE)
            .externalCustomerId("cus_test").build();
    }
}
