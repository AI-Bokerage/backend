package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.model.PurchaseAddOnCommand;
import com.company.bsmsvc.domain.model.AddOnPurchaseResult;
import com.company.bsmsvc.application.service.InvoiceGenerationService;
import com.company.bsmsvc.application.service.PaymentService;
import com.company.bsmsvc.domain.port.PpmAddOnPricingService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.exception.AddOnAlreadyPurchasedException;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.domain.exception.SubscriptionAddOnNotFoundException;
import com.company.bsmsvc.domain.exception.SubscriptionNotFoundException;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.SubscriptionAddOn;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.model.payment.CheckoutSessionResult;
import com.company.bsmsvc.domain.port.SubscriptionAddOnRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.service.TenantOwnershipValidator;
import com.company.bsmsvc.domain.model.PpmResolvedAddOnPriceResult;
import com.company.bsmsvc.domain.port.AddOnEventPublisherPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionAddOnServiceImplTest {

    @Mock SubscriptionRepositoryPort      subscriptionRepositoryPort;
    @Mock SubscriptionAddOnRepositoryPort subscriptionAddOnRepositoryPort;
    @Mock SubscriptionHistoryRepositoryPort subscriptionHistoryRepositoryPort;
    @Mock TenantBillingProfileService     tenantBillingProfileService;
    @Mock TenantOwnershipValidator        tenantOwnershipValidator;
    @Mock PpmAddOnPricingService          ppmAddOnPricingService;
    @Mock InvoiceGenerationService        invoiceGenerationService;
    @Mock PaymentService                  paymentService;
    @Mock AddOnEventPublisherPort         addOnEventPublisher;

    SubscriptionAddOnServiceImpl service;

    static final UUID SUBSCRIPTION_ID  = UUID.randomUUID();
    static final UUID TENANT_ID        = UUID.randomUUID();
    static final UUID PPM_ADD_ON_ID    = UUID.randomUUID();
    static final UUID PPM_PRICE_ID     = UUID.randomUUID();
    static final UUID ADD_ON_ROW_ID    = UUID.randomUUID();
    static final UUID INVOICE_ID       = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SubscriptionAddOnServiceImpl(
            subscriptionRepositoryPort,
            subscriptionAddOnRepositoryPort,
            subscriptionHistoryRepositoryPort,
            tenantBillingProfileService,
            tenantOwnershipValidator,
            ppmAddOnPricingService,
            invoiceGenerationService,
            paymentService,
            addOnEventPublisher
        );
    }

    // ── purchaseAddOn ─────────────────────────────────────────────────────────

    @Test
    void purchaseAddOn_success_savesAddOnAndReturnsCheckoutUrl() {
        Subscription sub = activeSubscription();
        PurchaseAddOnCommand req = purchaseRequest();
        PpmResolvedAddOnPriceResult resolved = resolvedPrice(new BigDecimal("499.00"));
        PlatformInvoice invoice = invoice();
        CheckoutSessionResult session = new CheckoutSessionResult("sess_001", "https://checkout.test/sess_001");
        SubscriptionAddOn savedAddOn = SubscriptionAddOn.builder()
            .id(ADD_ON_ROW_ID).subscriptionId(SUBSCRIPTION_ID).tenantId(TENANT_ID)
            .ppmAddOnId(PPM_ADD_ON_ID).ppmAddOnPriceId(PPM_PRICE_ID)
            .ppmResolvedPriceMinor(49900L).active(true).build();

        when(subscriptionRepositoryPort.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(sub));
        when(subscriptionAddOnRepositoryPort.existsActive(SUBSCRIPTION_ID, PPM_ADD_ON_ID)).thenReturn(false);
        when(tenantBillingProfileService.getProfile(TENANT_ID))
            .thenReturn(TenantBillingProfile.builder().currency("INR").build());
        when(ppmAddOnPricingService.resolveActivePrice(PPM_ADD_ON_ID, "INDIA", "INR", BillingCycle.MONTHLY))
            .thenReturn(resolved);
        when(subscriptionAddOnRepositoryPort.save(any())).thenReturn(savedAddOn);
        when(invoiceGenerationService.generateInvoice(any(), any(), any(), any(), any(), any(), anyList(), any()))
            .thenReturn(invoice);
        when(paymentService.createCheckoutSession(any(), any(), any(), any())).thenReturn(session);

        AddOnPurchaseResult response = service.purchaseAddOn(SUBSCRIPTION_ID, req);

        assertThat(response.subscriptionAddOnId()).isEqualTo(ADD_ON_ROW_ID);
        assertThat(response.invoiceId()).isEqualTo(INVOICE_ID);
        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.test/sess_001");
        assertThat(response.ppmResolvedPriceMinor()).isEqualTo(49900L);
        assertThat(response.currency()).isEqualTo("INR");
    }

    @Test
    void purchaseAddOn_subscriptionNotFound_throwsNotFoundException() {
        when(subscriptionRepositoryPort.findById(SUBSCRIPTION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.purchaseAddOn(SUBSCRIPTION_ID, purchaseRequest()))
            .isInstanceOf(SubscriptionNotFoundException.class);
    }

    @Test
    void purchaseAddOn_subscriptionNotActive_throwsBusinessRuleViolation() {
        Subscription sub = Subscription.builder()
            .id(SUBSCRIPTION_ID).tenantId(TENANT_ID)
            .status(SubscriptionStatus.CANCELLED)
            .currentPeriodStart(Instant.now().minusSeconds(3600))
            .currentPeriodEnd(Instant.now().plusSeconds(3600))
            .build();
        when(subscriptionRepositoryPort.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> service.purchaseAddOn(SUBSCRIPTION_ID, purchaseRequest()))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("ACTIVE");
    }

    @Test
    void purchaseAddOn_duplicateAddOn_throwsAddOnAlreadyPurchasedException() {
        when(subscriptionRepositoryPort.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(activeSubscription()));
        when(subscriptionAddOnRepositoryPort.existsActive(SUBSCRIPTION_ID, PPM_ADD_ON_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.purchaseAddOn(SUBSCRIPTION_ID, purchaseRequest()))
            .isInstanceOf(AddOnAlreadyPurchasedException.class);
    }

    @Test
    void purchaseAddOn_ppmResolveFailure_throwsPpmIntegrationException() {
        when(subscriptionRepositoryPort.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(activeSubscription()));
        when(subscriptionAddOnRepositoryPort.existsActive(SUBSCRIPTION_ID, PPM_ADD_ON_ID)).thenReturn(false);
        when(tenantBillingProfileService.getProfile(TENANT_ID))
            .thenReturn(TenantBillingProfile.builder().currency("INR").build());
        when(ppmAddOnPricingService.resolveActivePrice(any(), any(), any(), any()))
            .thenThrow(new PpmIntegrationException("PPM unavailable"));

        assertThatThrownBy(() -> service.purchaseAddOn(SUBSCRIPTION_ID, purchaseRequest()))
            .isInstanceOf(PpmIntegrationException.class);

        verify(subscriptionAddOnRepositoryPort, never()).save(any());
        verify(paymentService, never()).createCheckoutSession(any(), any(), any(), any());
    }

    @Test
    void purchaseAddOn_checkoutIsLastOperation_addOnAndInvoiceSavedBeforeCheckout() {
        SubscriptionAddOn savedAddOn = SubscriptionAddOn.builder()
            .id(ADD_ON_ROW_ID).subscriptionId(SUBSCRIPTION_ID).tenantId(TENANT_ID)
            .ppmAddOnId(PPM_ADD_ON_ID).ppmAddOnPriceId(PPM_PRICE_ID)
            .ppmResolvedPriceMinor(49900L).active(true).build();

        when(subscriptionRepositoryPort.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(activeSubscription()));
        when(subscriptionAddOnRepositoryPort.existsActive(SUBSCRIPTION_ID, PPM_ADD_ON_ID)).thenReturn(false);
        when(tenantBillingProfileService.getProfile(TENANT_ID))
            .thenReturn(TenantBillingProfile.builder().currency("INR").build());
        when(ppmAddOnPricingService.resolveActivePrice(any(), any(), any(), any()))
            .thenReturn(resolvedPrice(new BigDecimal("499.00")));
        when(subscriptionAddOnRepositoryPort.save(any())).thenReturn(savedAddOn);
        when(invoiceGenerationService.generateInvoice(any(), any(), any(), any(), any(), any(), anyList(), any()))
            .thenReturn(invoice());
        when(paymentService.createCheckoutSession(any(), any(), any(), any()))
            .thenReturn(new CheckoutSessionResult("sess_001", "https://checkout.test/sess_001"));

        service.purchaseAddOn(SUBSCRIPTION_ID, purchaseRequest());

        InOrder order = inOrder(subscriptionAddOnRepositoryPort, invoiceGenerationService,
            subscriptionHistoryRepositoryPort, addOnEventPublisher, paymentService);
        order.verify(subscriptionAddOnRepositoryPort).save(any());
        order.verify(invoiceGenerationService).generateInvoice(any(), any(), any(), any(), any(), any(), anyList(), any());
        order.verify(subscriptionHistoryRepositoryPort).save(any());
        order.verify(addOnEventPublisher).publishActivated(any());
        order.verify(paymentService).createCheckoutSession(any(), any(), any(), any());
    }

    @Test
    void purchaseAddOn_canRepurchaseAfterRemoval() {
        // Simulates: purchase → remove (active=false) → purchase again.
        // existsActive returns false because the partial unique index only covers active=true rows.
        SubscriptionAddOn savedAddOn = SubscriptionAddOn.builder()
            .id(ADD_ON_ROW_ID).subscriptionId(SUBSCRIPTION_ID).tenantId(TENANT_ID)
            .ppmAddOnId(PPM_ADD_ON_ID).ppmAddOnPriceId(PPM_PRICE_ID)
            .ppmResolvedPriceMinor(49900L).active(true).build();

        when(subscriptionRepositoryPort.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(activeSubscription()));
        when(subscriptionAddOnRepositoryPort.existsActive(SUBSCRIPTION_ID, PPM_ADD_ON_ID)).thenReturn(false);
        when(tenantBillingProfileService.getProfile(TENANT_ID))
            .thenReturn(TenantBillingProfile.builder().currency("INR").build());
        when(ppmAddOnPricingService.resolveActivePrice(PPM_ADD_ON_ID, "INDIA", "INR", BillingCycle.MONTHLY))
            .thenReturn(resolvedPrice(new BigDecimal("499.00")));
        when(subscriptionAddOnRepositoryPort.save(any())).thenReturn(savedAddOn);
        when(invoiceGenerationService.generateInvoice(any(), any(), any(), any(), any(), any(), anyList(), any()))
            .thenReturn(invoice());
        when(paymentService.createCheckoutSession(any(), any(), any(), any()))
            .thenReturn(new CheckoutSessionResult("sess_002", "https://checkout.test/sess_002"));

        AddOnPurchaseResult response = service.purchaseAddOn(SUBSCRIPTION_ID, purchaseRequest());

        assertThat(response.subscriptionAddOnId()).isEqualTo(ADD_ON_ROW_ID);
        assertThat(response.ppmResolvedPriceMinor()).isEqualTo(49900L);
    }

    // ── listAddOns ────────────────────────────────────────────────────────────

    @Test
    void listAddOns_returnsOnlyActiveAddOns() {
        SubscriptionAddOn addOn = SubscriptionAddOn.builder()
            .id(ADD_ON_ROW_ID).subscriptionId(SUBSCRIPTION_ID).ppmAddOnId(PPM_ADD_ON_ID)
            .ppmResolvedPriceMinor(49900L).active(true).build();

        when(subscriptionRepositoryPort.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(activeSubscription()));
        when(subscriptionAddOnRepositoryPort.findActiveBySubscriptionId(SUBSCRIPTION_ID))
            .thenReturn(List.of(addOn));

        List<SubscriptionAddOn> result = service.listAddOns(SUBSCRIPTION_ID, TENANT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPpmAddOnId()).isEqualTo(PPM_ADD_ON_ID);
    }

    // ── removeAddOn ───────────────────────────────────────────────────────────

    @Test
    void removeAddOn_success_deactivatesAddOn() {
        SubscriptionAddOn activeAddOn = SubscriptionAddOn.builder()
            .id(ADD_ON_ROW_ID).subscriptionId(SUBSCRIPTION_ID).tenantId(TENANT_ID)
            .ppmAddOnId(PPM_ADD_ON_ID).active(true).build();

        when(subscriptionRepositoryPort.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(activeSubscription()));
        when(subscriptionAddOnRepositoryPort.findActiveBySubscriptionIdAndPpmAddOnId(SUBSCRIPTION_ID, PPM_ADD_ON_ID))
            .thenReturn(Optional.of(activeAddOn));
        when(subscriptionAddOnRepositoryPort.save(any())).thenReturn(activeAddOn.toBuilder().active(false).build());

        service.removeAddOn(SUBSCRIPTION_ID, PPM_ADD_ON_ID, TENANT_ID, "admin");

        verify(subscriptionAddOnRepositoryPort).save(
            org.mockito.ArgumentMatchers.argThat(a -> !a.isActive()));
        verify(subscriptionHistoryRepositoryPort).save(any());
    }

    @Test
    void removeAddOn_notFound_throwsSubscriptionAddOnNotFoundException() {
        when(subscriptionRepositoryPort.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(activeSubscription()));
        when(subscriptionAddOnRepositoryPort.findActiveBySubscriptionIdAndPpmAddOnId(SUBSCRIPTION_ID, PPM_ADD_ON_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeAddOn(SUBSCRIPTION_ID, PPM_ADD_ON_ID, TENANT_ID, "admin"))
            .isInstanceOf(SubscriptionAddOnNotFoundException.class);
    }

    // ── factories ─────────────────────────────────────────────────────────────

    private Subscription activeSubscription() {
        return Subscription.builder()
            .id(SUBSCRIPTION_ID).tenantId(TENANT_ID)
            .status(SubscriptionStatus.ACTIVE)
            .currentPeriodStart(Instant.now().minusSeconds(3600))
            .currentPeriodEnd(Instant.now().plusSeconds(3600))
            .build();
    }

    private PurchaseAddOnCommand purchaseRequest() {
        return new PurchaseAddOnCommand(
            TENANT_ID, PPM_ADD_ON_ID, "INDIA", BillingCycle.MONTHLY,
            "https://success.test", "https://cancel.test", "user@test.com");
    }

    private PpmResolvedAddOnPriceResult resolvedPrice(BigDecimal amount) {
        return new PpmResolvedAddOnPriceResult(
            PPM_PRICE_ID, PPM_ADD_ON_ID, "MONTHLY", "INR", "INDIA", amount, false, null);
    }

    private PlatformInvoice invoice() {
        return PlatformInvoice.builder().id(INVOICE_ID).build();
    }
}
