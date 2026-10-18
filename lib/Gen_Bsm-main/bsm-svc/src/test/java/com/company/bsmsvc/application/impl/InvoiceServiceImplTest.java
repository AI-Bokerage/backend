package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.application.service.InvoiceGenerationService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.InvoiceLineItemType;
import com.company.bsmsvc.domain.enums.InvoiceStatus;
import com.company.bsmsvc.domain.model.InvoiceLineItem;
import com.company.bsmsvc.domain.model.PlatformInvoice;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.company.bsmsvc.domain.port.TenantScopePort;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.lenient;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceServiceImplTest {

    @Mock private PlatformInvoiceRepositoryPort platformInvoiceRepositoryPort;
    @Mock private InvoiceGenerationService invoiceGenerationService;
    @Mock private SubscriptionRepositoryPort subscriptionRepository;
    @Mock private com.company.bsmsvc.domain.port.SubscriptionAddOnRepositoryPort subscriptionAddOnRepository;
    @Mock private TenantBillingProfileService billingProfileService;
    @Mock private com.company.bsmsvc.domain.port.InvoiceEventPublisher invoiceEventPublisher;
    @Mock private TenantScopePort tenantScopeEnforcer;
    @Mock private com.company.bsmsvc.domain.port.EventPublisherPort auditEventPublisher;

    @InjectMocks private InvoiceServiceImpl invoiceService;

    private UUID invoiceId;

    @BeforeEach
    void setUp() {
        lenient().when(tenantScopeEnforcer.resolveEffectiveTenantId(any())).thenAnswer(inv -> inv.getArgument(0));
        invoiceId = UUID.randomUUID();
    }

    @Test
    void createInvoiceDelegatesToGenerationService() {
        // Provide explicit line items so auto-population is skipped
        InvoiceLineItem item = InvoiceLineItem.builder()
            .id(UUID.randomUUID()).itemType(InvoiceLineItemType.SUBSCRIPTION)
            .description("Monthly charge").quantity(1)
            .unitAmountMinor(1000L).amountMinor(1000L).createdAt(Instant.now()).build();

        PlatformInvoice draft = PlatformInvoice.builder()
            .tenantId(UUID.randomUUID()).subscriptionId(UUID.randomUUID()).currency("USD")
            .periodStart(Instant.now()).periodEnd(Instant.now().plusSeconds(86400))
            .dueDate(LocalDate.now().plusDays(15))
            .lineItems(new ArrayList<>(List.of(item)))
            .domainEvents(new ArrayList<>())
            .build();

        PlatformInvoice created = draft.toBuilder().id(invoiceId).build();
        when(invoiceGenerationService.generateInvoice(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(created);

        PlatformInvoice result = invoiceService.createInvoice(draft);

        assertThat(result).isEqualTo(created);
        verify(invoiceGenerationService).generateInvoice(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void applyPaymentMarksInvoicePaidWhenAmountNotProvided() {
        PlatformInvoice invoice = PlatformInvoice.builder()
            .id(invoiceId).tenantId(UUID.randomUUID()).subscriptionId(UUID.randomUUID())
            .invoiceNumber("INV-TEST").status(InvoiceStatus.OPEN)
            .amountDue(1000L).amountPaid(0L).currency("USD")
            .periodStart(Instant.now()).periodEnd(Instant.now().plusSeconds(86400))
            .dueDate(LocalDate.now().plusDays(15))
            .domainEvents(new ArrayList<>()).build();

        when(platformInvoiceRepositoryPort.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(platformInvoiceRepositoryPort.save(invoice)).thenReturn(invoice);

        PlatformInvoice result = invoiceService.applyPayment(invoiceId, null, null);

        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(result.getAmountPaid()).isEqualTo(1000L);
        verify(platformInvoiceRepositoryPort).save(invoice);
    }
}
