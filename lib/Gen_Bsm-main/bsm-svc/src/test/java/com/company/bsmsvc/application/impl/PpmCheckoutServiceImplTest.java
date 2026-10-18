package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.model.InitiateCheckoutCommand;
import com.company.bsmsvc.domain.model.CheckoutResult;
import com.company.bsmsvc.application.service.InvoiceGenerationService;
import com.company.bsmsvc.application.service.PaymentService;
import com.company.bsmsvc.domain.port.PpmPricingService;
import com.company.bsmsvc.domain.port.PpmPromoService;
import com.company.bsmsvc.domain.port.PpmVersionService;
import com.company.bsmsvc.application.service.SubscriptionService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.model.payment.CheckoutSessionResult;
import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.domain.model.PpmPlanVersionResult;
import com.company.bsmsvc.domain.model.PpmResolvePriceResult;
import com.company.bsmsvc.domain.model.PpmValidatePromoResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PpmCheckoutServiceImplTest {

    @Mock TenantBillingProfileService billingProfileService;
    @Mock PpmPricingService           ppmPricingService;
    @Mock PpmPromoService             ppmPromoService;
    @Mock PpmVersionService           ppmVersionService;
    @Mock SubscriptionService         subscriptionService;
    @Mock InvoiceGenerationService    invoiceGenerationService;
    @Mock PaymentService              paymentService;

    @InjectMocks
    PpmCheckoutServiceImpl service;

    UUID tenantId      = UUID.randomUUID();
    UUID planVerId     = UUID.randomUUID();
    UUID ppmPlanId     = UUID.randomUUID();
    UUID ppmVersionId  = UUID.randomUUID();
    UUID subId         = UUID.randomUUID();
    UUID invoiceId     = UUID.randomUUID();

    TenantBillingProfile profile;
    PpmResolvePriceResult priceResult;
    PpmPlanVersionResult versionResult;
    Subscription sub;
    PlatformInvoice invoice;
    CheckoutSessionResult sessionResult;

    @BeforeEach
    void setUp() {
        profile = TenantBillingProfile.builder()
            .tenantId(tenantId)
            .currency("INR")
            .build();

        priceResult = new PpmResolvePriceResult(
            ppmPlanId, UUID.randomUUID(), "monthly", "INR", "IN",
            new BigDecimal("999.00"), false, LocalDate.of(2026, 1, 1), true, 1L);

        versionResult = new PpmPlanVersionResult(
            ppmVersionId, ppmPlanId, 1, LocalDate.of(2026, 1, 1), null, true);

        sub = Subscription.builder()
            .id(subId)
            .tenantId(tenantId)
            .planVersionId(planVerId)
            .status(SubscriptionStatus.ACTIVE)
            .billingCycle(BillingCycle.MONTHLY)
            .currentPeriodStart(Instant.now())
            .currentPeriodEnd(Instant.now().plusSeconds(2592000))
            .build();

        invoice = PlatformInvoice.builder()
            .id(invoiceId)
            .tenantId(tenantId)
            .amountDue(99900L)
            .build();

        sessionResult = new CheckoutSessionResult("sess_abc", "https://checkout.stripe.com/pay/sess_abc");
    }

    @Test
    void initiateCheckout_noPromo_createsSessionSuccessfully() {
        InitiateCheckoutCommand request = new InitiateCheckoutCommand(
            tenantId, ppmPlanId, BillingCycle.MONTHLY, "IN",
            null, "https://success.io", "https://cancel.io", "admin", "test checkout");

        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(ppmPricingService.resolvePrice(ppmPlanId, "IN", "INR", "monthly")).thenReturn(priceResult);
        when(ppmVersionService.getLatestVersion(ppmPlanId)).thenReturn(versionResult);
        when(subscriptionService.createSubscription(any(), isNull(), nullable(String.class), anyString())).thenReturn(sub);
        when(invoiceGenerationService.generateInvoice(
            eq(tenantId), eq(subId), eq("INR"), any(), any(), any(), anyList(), any()))
            .thenReturn(invoice);
        when(paymentService.createCheckoutSession(tenantId, invoiceId,
            "https://success.io", "https://cancel.io")).thenReturn(sessionResult);

        CheckoutResult resp = service.initiateCheckout(request);

        assertThat(resp.subscriptionId()).isEqualTo(subId);
        assertThat(resp.invoiceId()).isEqualTo(invoiceId);
        assertThat(resp.checkoutUrl()).isEqualTo("https://checkout.stripe.com/pay/sess_abc");
        assertThat(resp.sessionId()).isEqualTo("sess_abc");
        assertThat(resp.resolvedAmountMinor()).isEqualTo(99900L);
        assertThat(resp.currency()).isEqualTo("INR");
        assertThat(resp.discountAmountMinor()).isNull();
        assertThat(resp.ppmPlanVersionId()).isEqualTo(ppmVersionId);

        verify(ppmPromoService, never()).validatePromo(anyString(), any());
    }

    @Test
    void initiateCheckout_withPercentagePromo_appliesDiscount() {
        InitiateCheckoutCommand request = new InitiateCheckoutCommand(
            tenantId, ppmPlanId, BillingCycle.MONTHLY, "IN",
            "SAVE10", "https://success.io", "https://cancel.io", "admin", "promo checkout");

        PpmValidatePromoResult promoResult = new PpmValidatePromoResult(
            true, "SAVE10", "percentage", new BigDecimal("10.00"), null, false);

        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(ppmPricingService.resolvePrice(ppmPlanId, "IN", "INR", "monthly")).thenReturn(priceResult);
        when(ppmVersionService.getLatestVersion(ppmPlanId)).thenReturn(versionResult);
        when(ppmPromoService.validatePromo("SAVE10", ppmPlanId)).thenReturn(promoResult);
        when(subscriptionService.createSubscription(any(), isNull(), nullable(String.class), anyString())).thenReturn(sub);
        when(invoiceGenerationService.generateInvoice(
            eq(tenantId), eq(subId), eq("INR"), any(), any(), any(), anyList(), any()))
            .thenReturn(invoice);
        when(paymentService.createCheckoutSession(tenantId, invoiceId,
            "https://success.io", "https://cancel.io")).thenReturn(sessionResult);

        CheckoutResult resp = service.initiateCheckout(request);

        // 10% of 99900 = 9990
        assertThat(resp.discountAmountMinor()).isEqualTo(9990L);
        assertThat(resp.resolvedAmountMinor()).isEqualTo(99900L);
    }

    @Test
    void initiateCheckout_withFlatPromo_appliesDiscount() {
        InitiateCheckoutCommand request = new InitiateCheckoutCommand(
            tenantId, ppmPlanId, BillingCycle.MONTHLY, "IN",
            "FLAT100", "https://success.io", "https://cancel.io", "admin", null);

        PpmValidatePromoResult promoResult = new PpmValidatePromoResult(
            true, "FLAT100", "flat", new BigDecimal("100.00"), null, false);

        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(ppmPricingService.resolvePrice(ppmPlanId, "IN", "INR", "monthly")).thenReturn(priceResult);
        when(ppmVersionService.getLatestVersion(ppmPlanId)).thenReturn(versionResult);
        when(ppmPromoService.validatePromo("FLAT100", ppmPlanId)).thenReturn(promoResult);
        when(subscriptionService.createSubscription(any(), isNull(), nullable(String.class), anyString())).thenReturn(sub);
        when(invoiceGenerationService.generateInvoice(
            eq(tenantId), eq(subId), eq("INR"), any(), any(), any(), anyList(), any()))
            .thenReturn(invoice);
        when(paymentService.createCheckoutSession(tenantId, invoiceId,
            "https://success.io", "https://cancel.io")).thenReturn(sessionResult);

        CheckoutResult resp = service.initiateCheckout(request);

        // flat 100.00 INR = 10000 paise
        assertThat(resp.discountAmountMinor()).isEqualTo(10000L);
    }

    @Test
    void initiateCheckout_invalidPromo_throwsBusinessRuleViolation() {
        InitiateCheckoutCommand request = new InitiateCheckoutCommand(
            tenantId, ppmPlanId, BillingCycle.MONTHLY, "IN",
            "EXPIRED", "https://success.io", "https://cancel.io", "admin", null);

        PpmValidatePromoResult promoResult = new PpmValidatePromoResult(
            false, "EXPIRED", null, null, "PROMO_EXPIRED", false);

        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(ppmPricingService.resolvePrice(ppmPlanId, "IN", "INR", "monthly")).thenReturn(priceResult);
        when(ppmVersionService.getLatestVersion(ppmPlanId)).thenReturn(versionResult);
        when(ppmPromoService.validatePromo("EXPIRED", ppmPlanId)).thenReturn(promoResult);

        assertThatThrownBy(() -> service.initiateCheckout(request))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("EXPIRED")
            .hasMessageContaining("PROMO_EXPIRED");

        verify(subscriptionService, never()).createSubscription(any(), any(), any(), any());
    }

    @Test
    void initiateCheckout_annualCycle_mapsToPpmAnnual() {
        InitiateCheckoutCommand request = new InitiateCheckoutCommand(
            tenantId, ppmPlanId, BillingCycle.YEARLY, "IN",
            null, "https://success.io", "https://cancel.io", "admin", null);

        PpmResolvePriceResult annualPrice = new PpmResolvePriceResult(
            ppmPlanId, UUID.randomUUID(), "annual", "INR", "IN",
            new BigDecimal("9999.00"), false, LocalDate.of(2026, 1, 1), true, 1L);

        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(ppmPricingService.resolvePrice(ppmPlanId, "IN", "INR", "annual")).thenReturn(annualPrice);
        when(ppmVersionService.getLatestVersion(ppmPlanId)).thenReturn(versionResult);
        when(subscriptionService.createSubscription(any(), isNull(), nullable(String.class), anyString())).thenReturn(sub);
        when(invoiceGenerationService.generateInvoice(
            eq(tenantId), eq(subId), eq("INR"), any(), any(), any(), anyList(), any()))
            .thenReturn(invoice);
        when(paymentService.createCheckoutSession(tenantId, invoiceId,
            "https://success.io", "https://cancel.io")).thenReturn(sessionResult);

        CheckoutResult resp = service.initiateCheckout(request);

        // 9999.00 INR = 999900 paise
        assertThat(resp.resolvedAmountMinor()).isEqualTo(999900L);
        verify(ppmPricingService).resolvePrice(ppmPlanId, "IN", "INR", "annual");
    }

    @Test
    void initiateCheckout_discountFloor_flatExceedsPrice_capsAtPrice() {
        // price = 1.00 INR = 100 minor, flat discount = 5.00 INR = 500 minor
        // Expected: discount capped at 100 (price), NOT -400 invoice total
        PpmResolvePriceResult lowPrice = new PpmResolvePriceResult(
            ppmPlanId, UUID.randomUUID(), "monthly", "INR", "IN",
            new BigDecimal("1.00"), false, LocalDate.of(2026, 1, 1), true, 1L);

        InitiateCheckoutCommand request = new InitiateCheckoutCommand(
            tenantId, ppmPlanId, BillingCycle.MONTHLY, "IN",
            "BIGFLAT", "https://success.io", "https://cancel.io", "admin", null);

        PpmValidatePromoResult promoResult = new PpmValidatePromoResult(
            true, "BIGFLAT", "flat", new BigDecimal("5.00"), null, false);

        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(ppmPricingService.resolvePrice(ppmPlanId, "IN", "INR", "monthly")).thenReturn(lowPrice);
        when(ppmVersionService.getLatestVersion(ppmPlanId)).thenReturn(versionResult);
        when(ppmPromoService.validatePromo("BIGFLAT", ppmPlanId)).thenReturn(promoResult);
        when(subscriptionService.createSubscription(any(), isNull(), nullable(String.class), anyString())).thenReturn(sub);
        when(invoiceGenerationService.generateInvoice(
            eq(tenantId), eq(subId), eq("INR"), any(), any(), any(), anyList(), any()))
            .thenReturn(invoice);
        when(paymentService.createCheckoutSession(tenantId, invoiceId,
            "https://success.io", "https://cancel.io")).thenReturn(sessionResult);

        CheckoutResult resp = service.initiateCheckout(request);

        // discount must be floored at the price (100 minor), never 500 minor
        assertThat(resp.resolvedAmountMinor()).isEqualTo(100L);
        assertThat(resp.discountAmountMinor()).isEqualTo(100L);
    }

    @Test
    void initiateCheckout_versionServiceFails_throwsPpmIntegrationException() {
        InitiateCheckoutCommand request = new InitiateCheckoutCommand(
            tenantId, ppmPlanId, BillingCycle.MONTHLY, "IN",
            null, "https://success.io", "https://cancel.io", "admin", null);

        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(ppmPricingService.resolvePrice(ppmPlanId, "IN", "INR", "monthly")).thenReturn(priceResult);
        when(ppmVersionService.getLatestVersion(ppmPlanId))
            .thenThrow(new PpmIntegrationException("PPM version service unavailable"));

        assertThatThrownBy(() -> service.initiateCheckout(request))
            .isInstanceOf(PpmIntegrationException.class)
            .hasMessageContaining("PPM version service unavailable");

        verify(subscriptionService, never()).createSubscription(any(), any(), any(), any());
    }

    @Test
    void initiateCheckout_pricingServiceFails_throwsPpmIntegrationException() {
        // F1: Pricing unavailable → checkout fails, no subscription created
        InitiateCheckoutCommand request = new InitiateCheckoutCommand(
            tenantId, ppmPlanId, BillingCycle.MONTHLY, "IN",
            null, "https://success.io", "https://cancel.io", "admin", null);

        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(ppmPricingService.resolvePrice(ppmPlanId, "IN", "INR", "monthly"))
            .thenThrow(new PpmIntegrationException("PPM pricing service unavailable"));

        assertThatThrownBy(() -> service.initiateCheckout(request))
            .isInstanceOf(PpmIntegrationException.class)
            .hasMessageContaining("PPM pricing service unavailable");

        verify(ppmVersionService, never()).getLatestVersion(any());
        verify(subscriptionService, never()).createSubscription(any(), any(), any(), any());
    }

    @Test
    void initiateCheckout_planIdMismatch_throwsPpmIntegrationException() {
        // K1: PPM returns price and version for different plans — must fail before persisting
        UUID differentPlanId = UUID.randomUUID();
        PpmResolvePriceResult mismatchedPrice = new PpmResolvePriceResult(
            differentPlanId, UUID.randomUUID(), "monthly", "INR", "IN",
            new BigDecimal("999.00"), false, LocalDate.of(2026, 1, 1), true, 1L);

        InitiateCheckoutCommand request = new InitiateCheckoutCommand(
            tenantId, ppmPlanId, BillingCycle.MONTHLY, "IN",
            null, "https://success.io", "https://cancel.io", "admin", null);

        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(ppmPricingService.resolvePrice(ppmPlanId, "IN", "INR", "monthly")).thenReturn(mismatchedPrice);
        when(ppmVersionService.getLatestVersion(ppmPlanId)).thenReturn(versionResult);

        assertThatThrownBy(() -> service.initiateCheckout(request))
            .isInstanceOf(PpmIntegrationException.class)
            .hasMessageContaining("PPM response mismatch");

        verify(subscriptionService, never()).createSubscription(any(), any(), any(), any());
    }
}
