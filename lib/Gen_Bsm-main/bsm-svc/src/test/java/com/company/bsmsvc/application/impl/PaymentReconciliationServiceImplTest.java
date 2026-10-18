package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.model.ReconciliationPolicy;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.enums.PaymentStatus;
import com.company.bsmsvc.domain.exception.TenantBillingProfileNotFoundException;
import com.company.bsmsvc.domain.model.Payment;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.model.payment.PaymentStatusResult;
import com.company.bsmsvc.domain.port.BillingLedgerRepositoryPort;
import com.company.bsmsvc.domain.port.PaymentGatewayPort;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
class PaymentReconciliationServiceImplTest {

    @Mock private PaymentRepositoryPort paymentRepository;
    @Mock private PaymentGatewayResolver resolver;
    @Mock private TenantBillingProfileService billingProfileService;
    @Mock private InvoiceService invoiceService;
    @Mock private BillingLedgerRepositoryPort ledgerRepository;
    private final ReconciliationPolicy properties = new ReconciliationPolicy(300);
    @Mock private PaymentGatewayPort gateway;
    @Mock private com.company.bsmsvc.domain.port.EventPublisherPort auditEventPublisher;
    private PaymentReconciliationServiceImpl reconciliationService;

    private UUID tenantId;
    private UUID invoiceId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();
        reconciliationService = new PaymentReconciliationServiceImpl(
            paymentRepository, resolver, billingProfileService, null, null,
            invoiceService, ledgerRepository, properties, auditEventPublisher);
        when(resolver.resolve(any())).thenReturn(gateway);
        when(ledgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void reconcilePendingPayments_skipsWhenNoPendingPayments() {
        when(paymentRepository.findPendingOlderThan(any())).thenReturn(List.of());

        reconciliationService.reconcilePendingPayments();

        verify(gateway, never()).retrievePaymentStatus(any());
    }

    @Test
    void reconcilePendingPayments_marksSucceededWhenProviderSucceeded() {
        Payment payment = pendingPayment();
        TenantBillingProfile profile = profile();
        when(paymentRepository.findPendingOlderThan(any())).thenReturn(List.of(payment));
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(gateway.retrievePaymentStatus("cs_test")).thenReturn(new PaymentStatusResult("cs_test", "complete", "ch_xxx", true, false));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceService.applyPayment(any(), any(), any())).thenAnswer(inv -> null);

        reconciliationService.reconcilePendingPayments();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(paymentRepository).save(payment);
        verify(invoiceService).applyPayment(invoiceId, 10000L, null);
        // INVOICE_PAID ledger entry is written inside PlatformInvoiceRepositoryAdapter.save()
        // via domain event — reconciliation no longer writes a duplicate explicit entry.
        verify(ledgerRepository, never()).save(argThat(e ->
            e instanceof com.company.bsmsvc.domain.model.BillingLedgerEntry ledger
            && ledger.getEntryType() == com.company.bsmsvc.domain.enums.LedgerEntryType.INVOICE_PAID));
    }

    @Test
    void reconcilePendingPayments_marksFailedWhenProviderFailed() {
        Payment payment = pendingPayment();
        TenantBillingProfile profile = profile();
        when(paymentRepository.findPendingOlderThan(any())).thenReturn(List.of(payment));
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(gateway.retrievePaymentStatus("cs_test")).thenReturn(new PaymentStatusResult("cs_test", "expired", null, false, true));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        reconciliationService.reconcilePendingPayments();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(payment);
    }

    @Test
    void reconcilePendingPayments_skipsSingleFailureAndContinues() {
        Payment failingPayment = pendingPayment();
        UUID tenantId2 = UUID.randomUUID();
        Payment goodPayment = Payment.builder()
            .id(UUID.randomUUID()).tenantId(tenantId2).invoiceId(UUID.randomUUID())
            .paymentProvider(PaymentProvider.STRIPE).externalPaymentId("cs_other")
            .status(PaymentStatus.PENDING).amountMinor(5000L).currency("INR")
            .createdAt(Instant.now().minusSeconds(600)).updatedAt(Instant.now())
            .build();
        when(paymentRepository.findPendingOlderThan(any())).thenReturn(List.of(failingPayment, goodPayment));
        when(billingProfileService.getProfile(tenantId)).thenThrow(new TenantBillingProfileNotFoundException("no profile"));
        TenantBillingProfile profile2 = TenantBillingProfile.builder()
            .id(UUID.randomUUID()).tenantId(tenantId2)
            .paymentProvider(PaymentProvider.STRIPE).externalCustomerId("cus_2")
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .domainEvents(new ArrayList<>()).build();
        when(billingProfileService.getProfile(tenantId2)).thenReturn(profile2);
        when(gateway.retrievePaymentStatus("cs_other")).thenReturn(new PaymentStatusResult("cs_other", "open", null, false, false));

        reconciliationService.reconcilePendingPayments();

        // first payment skipped, second processed without exception
        verify(gateway, never()).retrievePaymentStatus("cs_test");
        verify(gateway).retrievePaymentStatus("cs_other");
    }

    private Payment pendingPayment() {
        return Payment.builder()
            .id(UUID.randomUUID()).tenantId(tenantId).invoiceId(invoiceId)
            .paymentProvider(PaymentProvider.STRIPE).externalPaymentId("cs_test")
            .status(PaymentStatus.PENDING).amountMinor(10000L).currency("INR")
            .createdAt(Instant.now().minusSeconds(600)).updatedAt(Instant.now())
            .build();
    }

    private TenantBillingProfile profile() {
        return TenantBillingProfile.builder()
            .id(UUID.randomUUID()).tenantId(tenantId)
            .paymentProvider(PaymentProvider.STRIPE).externalCustomerId("cus_123")
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .domainEvents(new ArrayList<>()).build();
    }
}
