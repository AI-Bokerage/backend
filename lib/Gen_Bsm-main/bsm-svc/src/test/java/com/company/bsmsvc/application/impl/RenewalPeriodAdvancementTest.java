package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
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
class RenewalPeriodAdvancementTest {

    @Mock private SubscriptionRepositoryPort subscriptionRepository;
    @Mock private InvoiceService invoiceService;
    @Mock private PlatformInvoiceRepositoryPort platformInvoiceRepositoryPort;
    @Mock private TenantBillingProfileService billingProfileService;

    @InjectMocks private InvoiceRenewalServiceImpl renewalService;

    private UUID subscriptionId;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        subscriptionId = UUID.randomUUID();
        tenantId       = UUID.randomUUID();
        when(billingProfileService.getProfile(any())).thenReturn(
            TenantBillingProfile.builder().currency("INR").build());
    }

    // ── Test 1: Period advanced after successful renewal ──────────────────────

    @Test
    void generateRenewalInvoice_advancesSubscriptionPeriod() {
        Instant oldPeriodEnd = Instant.now().minusSeconds(60);
        Subscription sub = activeMonthlySubscription(oldPeriodEnd);
        when(invoiceService.createInvoice(any())).thenReturn(renewalInvoice());

        renewalService.generateRenewalInvoice(sub);

        ArgumentCaptor<Subscription> savedSub = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(savedSub.capture());

        Subscription updated = savedSub.getValue();
        // currentPeriodStart must equal the old period end
        assertThat(updated.getCurrentPeriodStart()).isEqualTo(oldPeriodEnd);
        // currentPeriodEnd must be ~30 days after the old period end
        assertThat(updated.getCurrentPeriodEnd())
            .isEqualTo(oldPeriodEnd.plusSeconds(30 * 86400L));
    }

    // ── Test 2: Yearly subscription advances by 365 days ─────────────────────

    @Test
    void generateRenewalInvoice_yearlySubscription_advances365Days() {
        Instant oldPeriodEnd = Instant.now().minusSeconds(60);
        Subscription sub = activeYearlySubscription(oldPeriodEnd);
        when(invoiceService.createInvoice(any())).thenReturn(renewalInvoice());

        renewalService.generateRenewalInvoice(sub);

        ArgumentCaptor<Subscription> savedSub = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(savedSub.capture());
        assertThat(savedSub.getValue().getCurrentPeriodEnd())
            .isEqualTo(oldPeriodEnd.plusSeconds(365 * 86400L));
    }

    // ── Test 3: Pre-check detects existing recurring invoice → period advanced without creation ──

    @Test
    void generateDueRenewalInvoices_duplicateDetected_selfHealsSubscriptionPeriod() {
        Instant oldPeriodEnd = Instant.now().minusSeconds(60);
        Subscription sub = activeMonthlySubscription(oldPeriodEnd);
        when(subscriptionRepository.findDueForRenewal(any())).thenReturn(List.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Simulate a recurring invoice already existing for this billing period
        when(platformInvoiceRepositoryPort.existsRecurringBySubscriptionIdAndPeriodStartAndPeriodEnd(
            any(), any(), any())).thenReturn(true);

        renewalService.generateDueRenewalInvoices();

        // subscriptionRepository.save must be called to advance the period
        verify(subscriptionRepository, atLeastOnce()).save(any(Subscription.class));
    }

    // ── Test 4: Period NOT advanced if invoice creation fails (non-duplicate) ─

    @Test
    void generateRenewalInvoice_periodSaveFailure_doesNotPropagate() {
        Instant oldPeriodEnd = Instant.now().minusSeconds(60);
        Subscription sub = activeMonthlySubscription(oldPeriodEnd);
        when(invoiceService.createInvoice(any())).thenReturn(renewalInvoice());
        when(subscriptionRepository.save(any())).thenThrow(new RuntimeException("DB down"));

        // Invoice creation succeeded — the save failure must be swallowed and the invoice returned
        PlatformInvoice result = renewalService.generateRenewalInvoice(sub);
        assertThat(result).isNotNull();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Subscription activeMonthlySubscription(Instant periodEnd) {
        return Subscription.builder()
            .id(subscriptionId).tenantId(tenantId)
            .status(SubscriptionStatus.ACTIVE).billingCycle(BillingCycle.MONTHLY)
            .currentPeriodStart(periodEnd.minusSeconds(2592000)).currentPeriodEnd(periodEnd)
            .domainEvents(new ArrayList<>()).build();
    }

    private Subscription activeYearlySubscription(Instant periodEnd) {
        return Subscription.builder()
            .id(subscriptionId).tenantId(tenantId)
            .status(SubscriptionStatus.ACTIVE).billingCycle(BillingCycle.YEARLY)
            .currentPeriodStart(periodEnd.minusSeconds(31536000)).currentPeriodEnd(periodEnd)
            .domainEvents(new ArrayList<>()).build();
    }

    private PlatformInvoice renewalInvoice() {
        return PlatformInvoice.builder()
            .id(UUID.randomUUID()).tenantId(tenantId).subscriptionId(subscriptionId)
            .invoiceNumber("INV-TEST").status(InvoiceStatus.OPEN)
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
