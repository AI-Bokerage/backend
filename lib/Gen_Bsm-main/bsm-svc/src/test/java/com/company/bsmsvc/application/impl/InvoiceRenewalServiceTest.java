package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.InvoicePdfStatus;
import com.company.bsmsvc.domain.enums.InvoiceSource;
import com.company.bsmsvc.domain.enums.InvoiceStatus;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
class InvoiceRenewalServiceTest {

    @Mock private SubscriptionRepositoryPort subscriptionRepository;
    @Mock private InvoiceService invoiceService;
    @Mock private PlatformInvoiceRepositoryPort platformInvoiceRepositoryPort;
    @Mock private TenantBillingProfileService billingProfileService;
    @Mock private com.company.bsmsvc.domain.port.SubscriptionEventPublisherPort subscriptionEventPublisher;

    @InjectMocks private InvoiceRenewalServiceImpl renewalService;

    private UUID tenantId;
    private UUID subscriptionId;

    @BeforeEach
    void setUp() {
        lenient().doNothing().when(subscriptionEventPublisher).publishRenewed(any());
        tenantId       = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        // default: no recurring invoice exists for any period
        when(platformInvoiceRepositoryPort.existsRecurringBySubscriptionIdAndPeriodStartAndPeriodEnd(
            any(), any(), any())).thenReturn(false);
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(billingProfileService.getProfile(any())).thenReturn(
            TenantBillingProfile.builder().currency("INR").build());
    }

    // ── Test 1: Happy path — renewal invoice generated ────────────────────────

    @Test
    void generateRenewalInvoice_callsInvoiceService_withRenewalSource() {
        Instant periodEnd = Instant.now().minusSeconds(60); // already past
        Subscription sub = activeSubscription(periodEnd, BillingCycle.MONTHLY);
        when(invoiceService.createInvoice(any())).thenReturn(generatedInvoice());

        renewalService.generateRenewalInvoice(sub);

        ArgumentCaptor<PlatformInvoice> captor = ArgumentCaptor.forClass(PlatformInvoice.class);
        verify(invoiceService).createInvoice(captor.capture());
        PlatformInvoice shell = captor.getValue();
        assertThat(shell.getSource()).isEqualTo(InvoiceSource.SUBSCRIPTION_RENEWAL);
        assertThat(shell.getSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(shell.getTenantId()).isEqualTo(tenantId);
        // periodStart = old period end
        assertThat(shell.getPeriodStart()).isEqualTo(periodEnd);
        // periodEnd = +30 days for MONTHLY
        assertThat(shell.getPeriodEnd()).isEqualTo(periodEnd.plusSeconds(30 * 86400L));
    }

    // ── Test 2: YEARLY subscription uses 365-day period ──────────────────────

    @Test
    void generateRenewalInvoice_yearlySubscription_uses365DayPeriod() {
        Instant periodEnd = Instant.now().minusSeconds(60);
        Subscription sub = activeSubscription(periodEnd, BillingCycle.YEARLY);
        when(invoiceService.createInvoice(any())).thenReturn(generatedInvoice());

        renewalService.generateRenewalInvoice(sub);

        ArgumentCaptor<PlatformInvoice> captor = ArgumentCaptor.forClass(PlatformInvoice.class);
        verify(invoiceService).createInvoice(captor.capture());
        assertThat(captor.getValue().getPeriodEnd()).isEqualTo(periodEnd.plusSeconds(365 * 86400L));
    }

    // ── Test 3: generateDueRenewalInvoices processes all due subscriptions ────

    @Test
    void generateDueRenewalInvoices_processesAllDue() {
        Instant now = Instant.now();
        Subscription sub1 = activeSubscription(now.minusSeconds(100), BillingCycle.MONTHLY);
        Subscription sub2 = activeSubscription(now.minusSeconds(200), BillingCycle.MONTHLY);
        when(subscriptionRepository.findDueForRenewal(any())).thenReturn(List.of(sub1, sub2));
        when(invoiceService.createInvoice(any())).thenReturn(generatedInvoice());

        renewalService.generateDueRenewalInvoices();

        verify(invoiceService, times(2)).createInvoice(any());
    }

    // ── Test 4 (updated): Scheduler skips creation when recurring invoice already exists ──

    @Test
    void generateDueRenewalInvoices_existingRecurringInvoice_skipsCreation() {
        Instant periodEnd = Instant.now().minusSeconds(60);
        Subscription sub = activeSubscription(periodEnd, BillingCycle.MONTHLY);
        when(subscriptionRepository.findDueForRenewal(any())).thenReturn(List.of(sub));

        // A recurring invoice already exists for this billing period
        when(platformInvoiceRepositoryPort.existsRecurringBySubscriptionIdAndPeriodStartAndPeriodEnd(
            eq(subscriptionId), any(), any())).thenReturn(true);

        renewalService.generateDueRenewalInvoices();

        // No invoice creation attempted
        verify(invoiceService, never()).createInvoice(any());
    }

    // ── Test 5: Scheduler advances period when recurring invoice already exists ──

    @Test
    void generateDueRenewalInvoices_existingRecurringInvoice_advancesSubscriptionPeriod() {
        Instant periodEnd = Instant.now().minusSeconds(60);
        Subscription sub = activeSubscription(periodEnd, BillingCycle.MONTHLY);
        when(subscriptionRepository.findDueForRenewal(any())).thenReturn(List.of(sub));
        when(platformInvoiceRepositoryPort.existsRecurringBySubscriptionIdAndPeriodStartAndPeriodEnd(
            eq(subscriptionId), any(), any())).thenReturn(true);

        renewalService.generateDueRenewalInvoices();

        // Period must be advanced so the scheduler loop terminates
        verify(subscriptionRepository).save(sub);
    }

    // ── Test 6: No subscriptions due — no invoice generation ─────────────────

    @Test
    void generateDueRenewalInvoices_noDueSubscriptions_doesNothing() {
        when(subscriptionRepository.findDueForRenewal(any())).thenReturn(List.of());

        renewalService.generateDueRenewalInvoices();

        verify(invoiceService, never()).createInvoice(any());
    }

    // ── Test 7: One unexpected failure does not abort processing of remaining subs ─

    @Test
    void generateDueRenewalInvoices_oneFailure_continuesRemainder() {
        Subscription sub1 = activeSubscription(Instant.now().minusSeconds(100), BillingCycle.MONTHLY);
        Subscription sub2 = activeSubscription(Instant.now().minusSeconds(200), BillingCycle.MONTHLY);
        when(subscriptionRepository.findDueForRenewal(any())).thenReturn(List.of(sub1, sub2));
        when(invoiceService.createInvoice(any()))
            .thenThrow(new RuntimeException("DB down"))
            .thenReturn(generatedInvoice());

        renewalService.generateDueRenewalInvoices();

        verify(invoiceService, times(2)).createInvoice(any());
    }

    // ── Test 8: Manual recurring invoice satisfies the billing period ─────────
    // A manual recurring invoice created before the scheduler fires must prevent the scheduler
    // from generating a second recurring invoice for the same period.

    @Test
    void generateDueRenewalInvoices_manualInvoiceCreatedFirst_schedulerSkips() {
        Instant periodEnd = Instant.now().minusSeconds(60);
        Subscription sub = activeSubscription(periodEnd, BillingCycle.MONTHLY);
        when(subscriptionRepository.findDueForRenewal(any())).thenReturn(List.of(sub));

        // Simulate a MANUAL invoice already committed for this period
        when(platformInvoiceRepositoryPort.existsRecurringBySubscriptionIdAndPeriodStartAndPeriodEnd(
            eq(subscriptionId), eq(periodEnd), any())).thenReturn(true);

        renewalService.generateDueRenewalInvoices();

        // Scheduler must not create another recurring invoice
        verify(invoiceService, never()).createInvoice(any());
        // Scheduler must still advance the period
        verify(subscriptionRepository).save(sub);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Subscription activeSubscription(Instant periodEnd, BillingCycle cycle) {
        return Subscription.builder()
            .id(subscriptionId).tenantId(tenantId)
            .status(SubscriptionStatus.ACTIVE).billingCycle(cycle)
            .currentPeriodStart(periodEnd.minusSeconds(2592000)).currentPeriodEnd(periodEnd)
            .domainEvents(new ArrayList<>()).build();
    }

    private PlatformInvoice generatedInvoice() {
        return PlatformInvoice.builder()
            .id(UUID.randomUUID()).tenantId(tenantId).subscriptionId(subscriptionId)
            .invoiceNumber("INV-RENEWAL").status(InvoiceStatus.OPEN)
            .source(InvoiceSource.SUBSCRIPTION_RENEWAL)
            .amountDue(9900L).amountPaid(0L).currency("INR")
            .periodStart(Instant.now()).periodEnd(Instant.now().plusSeconds(2592000))
            .dueDate(LocalDate.now().plusDays(7))
            .lineItems(new ArrayList<>()).domainEvents(new ArrayList<>())
            .pdfGenerationStatus(InvoicePdfStatus.PENDING)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();
    }
}
