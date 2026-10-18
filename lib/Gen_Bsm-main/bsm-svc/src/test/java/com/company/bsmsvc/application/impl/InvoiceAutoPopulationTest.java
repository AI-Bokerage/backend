package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.application.service.InvoiceGenerationService;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.InvoiceLineItemType;
import com.company.bsmsvc.domain.enums.InvoicePdfStatus;
import com.company.bsmsvc.domain.enums.InvoiceSource;
import com.company.bsmsvc.domain.enums.InvoiceStatus;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.model.InvoiceLineItem;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
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
class InvoiceAutoPopulationTest {

    @Mock private PlatformInvoiceRepositoryPort invoiceRepository;
    @Mock private InvoiceGenerationService invoiceGenerationService;
    @Mock private SubscriptionRepositoryPort subscriptionRepository;
    @Mock private com.company.bsmsvc.domain.port.SubscriptionAddOnRepositoryPort subscriptionAddOnRepository;
    @Mock private TenantScopePort tenantScopeEnforcer;

    @InjectMocks private InvoiceServiceImpl invoiceService;

    private UUID tenantId;
    private UUID subscriptionId;

    @BeforeEach
    void setUp() {
        lenient().when(tenantScopeEnforcer.resolveEffectiveTenantId(any())).thenAnswer(inv -> inv.getArgument(0));
        tenantId      = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();

        when(invoiceGenerationService.generateInvoice(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenAnswer(inv -> {
                List<InvoiceLineItem> items = inv.getArgument(6);
                return PlatformInvoice.builder()
                    .id(UUID.randomUUID()).tenantId(tenantId).subscriptionId(subscriptionId)
                    .invoiceNumber("INV-001").status(InvoiceStatus.OPEN)
                    .source(inv.getArgument(7))
                    .amountDue(items == null || items.isEmpty() ? 0L : items.get(0).getAmountMinor())
                    .amountPaid(0L).currency("INR")
                    .periodStart(inv.getArgument(3)).periodEnd(inv.getArgument(4))
                    .dueDate(inv.getArgument(5))
                    .lineItems(items == null ? new ArrayList<>() : new ArrayList<>(items))
                    .domainEvents(new ArrayList<>())
                    .pdfGenerationStatus(InvoicePdfStatus.PENDING)
                    .createdAt(Instant.now()).updatedAt(Instant.now())
                    .build();
            });
    }

    // ── Test: Custom invoice with explicit line items uses them as-is ──────────

    @Test
    void customInvoice_withExplicitLineItems_usesProvidedItems() {
        InvoiceLineItem customItem = InvoiceLineItem.builder()
            .id(UUID.randomUUID()).itemType(InvoiceLineItemType.ADDON)
            .description("Custom Add-on").quantity(2)
            .unitAmountMinor(5000L).amountMinor(10000L)
            .createdAt(Instant.now()).build();

        PlatformInvoice invoice = emptyLineItemsInvoice().toBuilder()
            .lineItems(new ArrayList<>(List.of(customItem)))
            .source(InvoiceSource.MANUAL)
            .build();

        invoiceService.createInvoice(invoice);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InvoiceLineItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(invoiceGenerationService).generateInvoice(any(), any(), any(), any(), any(), any(), itemsCaptor.capture(), eq(InvoiceSource.MANUAL));

        assertThat(itemsCaptor.getValue()).hasSize(1);
        assertThat(itemsCaptor.getValue().get(0).getDescription()).isEqualTo("Custom Add-on");
    }

    // ── Test: PPM-backed subscription — locked price used, no plan repo calls ──

    @Test
    @SuppressWarnings("unchecked")
    void ppmBackedSubscription_usesLockedPrice_noPlanRepositoryQuery() {
        UUID planVersionId = UUID.randomUUID();
        Subscription ppmSub = Subscription.builder()
            .id(subscriptionId).tenantId(tenantId).planVersionId(planVersionId)
            .status(SubscriptionStatus.ACTIVE).billingCycle(BillingCycle.MONTHLY)
            .ppmPlanId(UUID.randomUUID()).ppmPriceId(UUID.randomUUID())
            .ppmPlanVersionId(UUID.randomUUID()).ppmResolvedPriceMinor(99900L)
            .currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now().plusSeconds(2592000))
            .domainEvents(new ArrayList<>()).build();

        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(ppmSub));
        when(subscriptionAddOnRepository.findActiveBySubscriptionId(subscriptionId)).thenReturn(List.of());

        invoiceService.createInvoice(emptyLineItemsInvoice());

        ArgumentCaptor<List<InvoiceLineItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(invoiceGenerationService).generateInvoice(any(), any(), any(), any(), any(), any(), itemsCaptor.capture(), any());

        List<InvoiceLineItem> items = itemsCaptor.getValue();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getItemType()).isEqualTo(InvoiceLineItemType.SUBSCRIPTION);
        assertThat(items.get(0).getUnitAmountMinor()).isEqualTo(99900L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PlatformInvoice emptyLineItemsInvoice() {
        return PlatformInvoice.builder()
            .id(UUID.randomUUID()).tenantId(tenantId).subscriptionId(subscriptionId)
            .currency("INR")
            .periodStart(Instant.now()).periodEnd(Instant.now().plusSeconds(2592000))
            .dueDate(LocalDate.now().plusDays(7))
            .status(InvoiceStatus.DRAFT).amountDue(0L).amountPaid(0L)
            .lineItems(new ArrayList<>()).domainEvents(new ArrayList<>())
            .pdfGenerationStatus(InvoicePdfStatus.PENDING)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();
    }
}
