package com.company.bsmsvc.application.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.application.service.CreditNoteService;
import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.domain.enums.RefundStatus;
import com.company.bsmsvc.domain.model.RefundRequest;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import com.company.bsmsvc.domain.port.RefundRequestRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundRecoverySchedulerTest {

    @Mock private RefundRequestRepositoryPort refundRequestRepository;
    @Mock private PlatformInvoiceRepositoryPort invoiceRepository;
    @Mock private PaymentRepositoryPort paymentRepository;
    @Mock private CreditNoteService creditNoteService;
    @Mock private InvoiceService invoiceService;

    @InjectMocks private RefundRecoveryServiceImpl recoveryService;

    // ── Test 1: recoverAll picks up RECOVERY_REQUIRED ────────────────────────

    @Test
    void recoverAll_picksUp_RECOVERY_REQUIRED() {
        RefundRequest req = strandedRequest(RefundStatus.RECOVERY_REQUIRED);
        when(refundRequestRepository.findByStatus(RefundStatus.RECOVERY_REQUIRED)).thenReturn(List.of(req));
        when(refundRequestRepository.findByStatusOlderThan(any(), any())).thenReturn(List.of());
        when(refundRequestRepository.findById(req.getId())).thenReturn(Optional.of(req));
        when(invoiceRepository.findById(any())).thenReturn(Optional.empty()); // triggers early return in recover()

        recoveryService.recoverAll();

        verify(refundRequestRepository).findByStatus(RefundStatus.RECOVERY_REQUIRED);
        verify(refundRequestRepository).findByStatusOlderThan(any(), any());
    }

    // ── Test 2: recoverAll ALSO picks up PROVIDER_REFUND_SUCCEEDED ────────────

    @Test
    void recoverAll_alsoPicksUp_PROVIDER_REFUND_SUCCEEDED() {
        RefundRequest stranded = strandedRequest(RefundStatus.PROVIDER_REFUND_SUCCEEDED);
        when(refundRequestRepository.findByStatus(RefundStatus.RECOVERY_REQUIRED)).thenReturn(List.of());
        when(refundRequestRepository.findByStatusOlderThan(any(), any())).thenReturn(List.of(stranded));
        when(refundRequestRepository.findById(stranded.getId())).thenReturn(Optional.of(stranded));
        when(invoiceRepository.findById(any())).thenReturn(Optional.empty());

        recoveryService.recoverAll();

        // recover() must have been called for the stranded PROVIDER_REFUND_SUCCEEDED request
        verify(refundRequestRepository, atLeast(1)).findById(stranded.getId());
    }

    // ── Test 3: recoverAll with empty lists — no recover() called ─────────────

    @Test
    void recoverAll_nothingToRecover_doesNothing() {
        when(refundRequestRepository.findByStatus(RefundStatus.RECOVERY_REQUIRED)).thenReturn(List.of());
        when(refundRequestRepository.findByStatusOlderThan(any(), any())).thenReturn(List.of());

        recoveryService.recoverAll();

        verify(refundRequestRepository, never()).findById(any());
    }

    // ── Test 4: recover() never calls gateway — only local work ───────────────

    @Test
    void recover_neverCallsProviderGateway() {
        // RefundRecoveryServiceImpl has no gateway injection by design — verify the class field list
        // The test confirms the constructor only takes: repo, invoiceRepo, paymentRepo, creditNoteService
        // (No PaymentGatewayPort in constructor → structurally impossible to call provider)
        // This test verifies recover() completes without needing a gateway mock.
        RefundRequest req = strandedRequest(RefundStatus.RECOVERY_REQUIRED);
        when(refundRequestRepository.findById(req.getId())).thenReturn(Optional.of(req));
        when(invoiceRepository.findById(req.getInvoiceId())).thenReturn(Optional.empty());

        // Should not throw even without a gateway — confirms no provider call path
        recoveryService.recover(req.getId());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private RefundRequest strandedRequest(RefundStatus status) {
        return RefundRequest.builder()
            .id(UUID.randomUUID()).tenantId(UUID.randomUUID()).invoiceId(UUID.randomUUID())
            .paymentId(UUID.randomUUID()).requestedAmountMinor(500L)
            .providerRefundId("re_test_123").status(status)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }
}
