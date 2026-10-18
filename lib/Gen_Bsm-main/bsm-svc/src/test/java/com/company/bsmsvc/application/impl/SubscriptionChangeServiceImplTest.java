package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.model.ApplyPlanChangeCommand;
import com.company.bsmsvc.domain.model.PreviewPlanChangeCommand;
import com.company.bsmsvc.domain.model.PlanChangeApplyResult;
import com.company.bsmsvc.domain.model.PlanChangePreviewResult;
import com.company.bsmsvc.application.service.InvoiceGenerationService;
import com.company.bsmsvc.application.service.PaymentService;
import com.company.bsmsvc.domain.port.PpmPricingService;
import com.company.bsmsvc.domain.port.PpmPromoService;
import com.company.bsmsvc.domain.port.PpmVersionService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.PpmPlanChangeType;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.model.PpmChangeSnapshot;
import com.company.bsmsvc.domain.model.PpmProrationResult;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.domain.exception.SubscriptionNotFoundException;
import com.company.bsmsvc.domain.model.InvoiceLineItem;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.model.payment.CheckoutSessionResult;
import com.company.bsmsvc.domain.port.PpmChangeSnapshotRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionEventRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.service.PpmProrationEngine;
import com.company.bsmsvc.domain.service.TenantOwnershipValidator;
import com.company.bsmsvc.domain.model.PpmPlanVersionResult;
import com.company.bsmsvc.domain.model.PpmResolvePriceResult;
import com.company.bsmsvc.domain.model.PpmValidatePromoResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionChangeServiceImplTest {

    @Mock SubscriptionRepositoryPort        subscriptionRepositoryPort;
    @Mock SubscriptionHistoryRepositoryPort subscriptionHistoryRepositoryPort;
    @Mock SubscriptionEventRepositoryPort   subscriptionEventRepositoryPort;
    @Mock PpmChangeSnapshotRepositoryPort   ppmChangeSnapshotRepositoryPort;
    @Mock TenantBillingProfileService       tenantBillingProfileService;
    @Mock TenantOwnershipValidator          tenantOwnershipValidator;
    @Mock PpmPricingService                 ppmPricingService;
    @Mock PpmVersionService                 ppmVersionService;
    @Mock PpmPromoService                   ppmPromoService;
    @Spy  PpmProrationEngine                prorationEngine = new PpmProrationEngine();
    @Mock InvoiceGenerationService          invoiceGenerationService;
    @Mock PaymentService                    paymentService;

    @InjectMocks
    SubscriptionChangeServiceImpl service;

    UUID tenantId        = UUID.randomUUID();
    UUID subscriptionId  = UUID.randomUUID();
    UUID currentPpmPlanId = UUID.randomUUID();
    UUID targetPpmPlanId  = UUID.randomUUID();
    UUID targetPpmPriceId = UUID.randomUUID();
    UUID targetVersionId  = UUID.randomUUID();
    UUID invoiceId        = UUID.randomUUID();

    // 30-day period; change at halfway → fraction ≈ 0.5
    Instant periodStart = Instant.parse("2026-06-01T00:00:00Z");
    Instant periodEnd   = Instant.parse("2026-07-01T00:00:00Z");

    Subscription activePpmSub;
    TenantBillingProfile profile;
    PpmResolvePriceResult resolvedPrice;
    PpmPlanVersionResult  planVersion;
    PlatformInvoice       invoice;
    CheckoutSessionResult sessionResult;

    @BeforeEach
    void setUp() {
        activePpmSub = Subscription.builder()
            .id(subscriptionId)
            .tenantId(tenantId)
            .status(SubscriptionStatus.ACTIVE)
            .billingCycle(BillingCycle.MONTHLY)
            .currentPeriodStart(periodStart)
            .currentPeriodEnd(periodEnd)
            .ppmPlanId(currentPpmPlanId)
            .ppmPriceId(UUID.randomUUID())
            .ppmPlanVersionId(UUID.randomUUID())
            .ppmResolvedPriceMinor(100_000L)   // 1000.00 INR
            .build();

        profile = TenantBillingProfile.builder()
            .tenantId(tenantId)
            .currency("INR")
            .build();

        // target plan costs 2000.00 INR (upgrade)
        resolvedPrice = new PpmResolvePriceResult(
            targetPpmPlanId, targetPpmPriceId, "monthly", "INR", "IN",
            new BigDecimal("2000.00"), false, LocalDate.of(2026, 1, 1), true, 1L);

        planVersion = new PpmPlanVersionResult(
            targetVersionId, targetPpmPlanId, 1, LocalDate.of(2026, 1, 1), null, true);

        invoice = PlatformInvoice.builder()
            .id(invoiceId)
            .tenantId(tenantId)
            .subscriptionId(subscriptionId)
            .amountDue(100_000L)
            .currency("INR")
            .build();

        sessionResult = new CheckoutSessionResult("sess_test_123", "https://checkout.test/session");
    }

    // ── previewChange ─────────────────────────────────────────────────────────

    @Test
    void previewChange_upgradeReturnsCorrectPreview() {
        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(activePpmSub));
        when(tenantBillingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(ppmPricingService.resolvePrice(eq(targetPpmPlanId), any(), any(), any())).thenReturn(resolvedPrice);
        when(ppmVersionService.getLatestVersion(targetPpmPlanId)).thenReturn(planVersion);

        PreviewPlanChangeCommand request = new PreviewPlanChangeCommand(
            tenantId, targetPpmPlanId, "IN", BillingCycle.MONTHLY, null);

        PlanChangePreviewResult preview = service.previewChange(subscriptionId, request);

        assertThat(preview.changeType()).isEqualTo(PpmPlanChangeType.UPGRADE);
        assertThat(preview.targetPpmResolvedPriceMinor()).isEqualTo(200_000L);
        assertThat(preview.currency()).isEqualTo("INR");
        assertThat(preview.currentPpmPlanId()).isEqualTo(currentPpmPlanId);
        assertThat(preview.targetPpmPlanId()).isEqualTo(targetPpmPlanId);
        assertThat(preview.targetPpmPlanVersionId()).isEqualTo(targetVersionId);
        // No writes
        verify(subscriptionRepositoryPort, never()).save(any());
        verify(ppmChangeSnapshotRepositoryPort, never()).save(any());
    }

    @Test
    void previewChange_subscriptionNotFound_throwsNotFoundException() {
        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.previewChange(subscriptionId,
            new PreviewPlanChangeCommand(tenantId, targetPpmPlanId, "IN", BillingCycle.MONTHLY, null)))
            .isInstanceOf(SubscriptionNotFoundException.class);
    }

    @Test
    void previewChange_notPpmSubscription_throwsBusinessRuleViolation() {
        Subscription nativeSub = activePpmSub.toBuilder().ppmPlanId(null).build();
        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(nativeSub));

        assertThatThrownBy(() -> service.previewChange(subscriptionId,
            new PreviewPlanChangeCommand(tenantId, targetPpmPlanId, "IN", BillingCycle.MONTHLY, null)))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("not created through PPM checkout");
    }

    // ── applyChange — validations ─────────────────────────────────────────────

    @Test
    void applyChange_subscriptionNotActive_throwsBusinessRuleViolation() {
        Subscription cancelled = activePpmSub.toBuilder().status(SubscriptionStatus.CANCELLED).build();
        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.applyChange(subscriptionId, makeApplyRequest()))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("ACTIVE");
    }

    @Test
    void applyChange_samePlanSamePrice_throwsBusinessRuleViolation() {
        // target resolves to the SAME price ID as current subscription
        PpmResolvePriceResult samePrice = new PpmResolvePriceResult(
            currentPpmPlanId, activePpmSub.getPpmPriceId(), "monthly", "INR", "IN",
            new BigDecimal("1000.00"), false, LocalDate.of(2026, 1, 1), true, 1L);
        PpmPlanVersionResult sameVersion = new PpmPlanVersionResult(
            UUID.randomUUID(), currentPpmPlanId, 1, LocalDate.of(2026, 1, 1), null, true);

        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(activePpmSub));
        when(tenantBillingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(ppmPricingService.resolvePrice(eq(currentPpmPlanId), any(), any(), any())).thenReturn(samePrice);
        when(ppmVersionService.getLatestVersion(currentPpmPlanId)).thenReturn(sameVersion);

        ApplyPlanChangeCommand request = new ApplyPlanChangeCommand(
            tenantId, currentPpmPlanId, "IN", BillingCycle.MONTHLY,
            null, null, "https://success.test", "https://cancel.test", null);

        assertThatThrownBy(() -> service.applyChange(subscriptionId, request))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("identical");
    }

    @Test
    void applyChange_k1Mismatch_throwsPpmIntegrationException() {
        UUID differentPlanId = UUID.randomUUID();
        // version reports a different planId than price
        PpmPlanVersionResult mismatchedVersion = new PpmPlanVersionResult(
            targetVersionId, differentPlanId, 1, LocalDate.of(2026, 1, 1), null, true);

        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(activePpmSub));
        when(tenantBillingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(ppmPricingService.resolvePrice(any(), any(), any(), any())).thenReturn(resolvedPrice);
        when(ppmVersionService.getLatestVersion(any())).thenReturn(mismatchedVersion);

        assertThatThrownBy(() -> service.applyChange(subscriptionId, makeApplyRequest()))
            .isInstanceOf(PpmIntegrationException.class)
            .hasMessageContaining("PPM response mismatch");
    }

    @Test
    void applyChange_pricingServiceFails_throwsPpmIntegrationException() {
        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(activePpmSub));
        when(tenantBillingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(ppmPricingService.resolvePrice(any(), any(), any(), any()))
            .thenThrow(new PpmIntegrationException("PPM unavailable"));

        assertThatThrownBy(() -> service.applyChange(subscriptionId, makeApplyRequest()))
            .isInstanceOf(PpmIntegrationException.class);

        verify(subscriptionRepositoryPort, never()).save(any());
    }

    // ── C5: promo preview ────────────────────────────────────────────────────

    @Test
    void previewChange_withInvalidPromo_throwsBusinessRuleViolation() {
        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(activePpmSub));
        when(tenantBillingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(ppmPricingService.resolvePrice(eq(targetPpmPlanId), any(), any(), any())).thenReturn(resolvedPrice);
        when(ppmVersionService.getLatestVersion(targetPpmPlanId)).thenReturn(planVersion);
        when(ppmPromoService.validatePromo("EXPIRED", targetPpmPlanId))
            .thenReturn(new PpmValidatePromoResult(false, "EXPIRED", null, null, "promo_expired", false));

        assertThatThrownBy(() -> service.previewChange(subscriptionId,
            new PreviewPlanChangeCommand(tenantId, targetPpmPlanId, "IN", BillingCycle.MONTHLY, "EXPIRED")))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("not valid");
    }

    @Test
    void previewChange_promoIgnoredForDowngrade() {
        // Promo is validated early (D1 order), but discount = 0 because net <= 0 after proration (PC-8).
        // "Called but ignored" — G1 spec explicitly allows either optimization.
        PpmResolvePriceResult cheaperPrice = new PpmResolvePriceResult(
            targetPpmPlanId, targetPpmPriceId, "monthly", "INR", "IN",
            new BigDecimal("500.00"), false, LocalDate.of(2026, 1, 1), true, 1L);

        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(activePpmSub));
        when(tenantBillingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(ppmPricingService.resolvePrice(eq(targetPpmPlanId), any(), any(), any())).thenReturn(cheaperPrice);
        when(ppmVersionService.getLatestVersion(targetPpmPlanId)).thenReturn(planVersion);
        when(ppmPromoService.validatePromo("SAVE20", targetPpmPlanId))
            .thenReturn(new PpmValidatePromoResult(true, "SAVE20", "percentage", new BigDecimal("20"), "valid", false));

        PlanChangePreviewResult preview = service.previewChange(subscriptionId,
            new PreviewPlanChangeCommand(tenantId, targetPpmPlanId, "IN", BillingCycle.MONTHLY, "SAVE20"));

        assertThat(preview.changeType()).isEqualTo(PpmPlanChangeType.DOWNGRADE);
        assertThat(preview.promoDiscountMinor()).isEqualTo(0L);
    }

    // ── C5: promo apply ──────────────────────────────────────────────────────

    @Test
    void applyChange_upgradeWithFlatPromo_discountCappedAtCharge() {
        // Flat promo of ₹99999 (absurdly large) must be capped at actual charge (PC-5)
        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(activePpmSub));
        when(tenantBillingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(ppmPricingService.resolvePrice(eq(targetPpmPlanId), any(), any(), any())).thenReturn(resolvedPrice);
        when(ppmVersionService.getLatestVersion(targetPpmPlanId)).thenReturn(planVersion);
        when(ppmPromoService.validatePromo("BIGFLAT", targetPpmPlanId))
            .thenReturn(new PpmValidatePromoResult(true, "BIGFLAT", "flat", new BigDecimal("99999"), "valid", false));
        when(subscriptionRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ppmChangeSnapshotRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlanChangeApplyResult response = service.applyChange(subscriptionId, makeApplyRequestWithPromo("BIGFLAT"));

        // discount capped → discounted charge = 0 → discountedNet may be negative → no invoice
        assertThat(response.invoiceId()).isNull();
        assertThat(response.checkoutUrl()).isNull();
        verify(invoiceGenerationService, never())
            .generateInvoice(any(), any(), any(), any(), any(), any(), anyList(), any());
        verify(paymentService, never()).createCheckoutSession(any(), any(), any(), any());
    }

    @Test
    void applyChange_invalidPromo_failsClosedBeforeAnyWrite() {
        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(activePpmSub));
        when(tenantBillingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(ppmPricingService.resolvePrice(eq(targetPpmPlanId), any(), any(), any())).thenReturn(resolvedPrice);
        when(ppmVersionService.getLatestVersion(targetPpmPlanId)).thenReturn(planVersion);
        when(ppmPromoService.validatePromo("BAD", targetPpmPlanId))
            .thenReturn(new PpmValidatePromoResult(false, "BAD", null, null, "usage_cap_reached", false));

        assertThatThrownBy(() -> service.applyChange(subscriptionId, makeApplyRequestWithPromo("BAD")))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("not valid");

        verify(subscriptionRepositoryPort, never()).save(any());
        verify(invoiceGenerationService, never())
            .generateInvoice(any(), any(), any(), any(), any(), any(), anyList(), any());
        verify(paymentService, never()).createCheckoutSession(any(), any(), any(), any());
    }

    // ── C5: blank promo & end-to-end financial chain ─────────────────────────

    @Test
    void previewChange_blankPromo_treatedAsNoPromo() {
        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(activePpmSub));
        when(tenantBillingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(ppmPricingService.resolvePrice(eq(targetPpmPlanId), any(), any(), any())).thenReturn(resolvedPrice);
        when(ppmVersionService.getLatestVersion(targetPpmPlanId)).thenReturn(planVersion);

        PlanChangePreviewResult preview = service.previewChange(subscriptionId,
            new PreviewPlanChangeCommand(tenantId, targetPpmPlanId, "IN", BillingCycle.MONTHLY, "   "));

        assertThat(preview.promoDiscountMinor()).isEqualTo(0L);
        verify(ppmPromoService, never()).validatePromo(any(), any());
    }

    @Test
    void applyChange_e2e_flatPromo_fullFinancialChain() {
        // Starter → Growth: Credit=₹500, Charge=₹1000, FlatPromo=₹200
        // Expected: Discount=₹200, DiscountedNet=₹300, 3 invoice items, snapshot stores promo
        long creditMinor        = 50_000L;   // ₹500
        long chargeMinor        = 100_000L;  // ₹1000
        long rawNetMinor        = chargeMinor - creditMinor;   // ₹500
        long expectedDiscount   = 20_000L;   // ₹200
        long expectedNet        = chargeMinor - expectedDiscount - creditMinor; // ₹300

        PpmProrationResult fixedProration = new PpmProrationResult(
            creditMinor, chargeMinor, rawNetMinor, BigDecimal.valueOf(0.5), PpmPlanChangeType.UPGRADE);
        doReturn(fixedProration).when(prorationEngine)
            .calculate(anyLong(), anyLong(), any(), any(), any());

        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(activePpmSub));
        when(tenantBillingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(ppmPricingService.resolvePrice(eq(targetPpmPlanId), any(), any(), any())).thenReturn(resolvedPrice);
        when(ppmVersionService.getLatestVersion(targetPpmPlanId)).thenReturn(planVersion);
        when(ppmPromoService.validatePromo("FLAT200", targetPpmPlanId))
            .thenReturn(new PpmValidatePromoResult(true, "FLAT200", "flat", new BigDecimal("200"), "valid", false));
        when(subscriptionRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ppmChangeSnapshotRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceGenerationService.generateInvoice(any(), any(), any(), any(), any(), any(), anyList(), any()))
            .thenReturn(invoice);
        when(paymentService.createCheckoutSession(any(), any(), any(), any())).thenReturn(sessionResult);

        PlanChangeApplyResult response = service.applyChange(subscriptionId, makeApplyRequestWithPromo("FLAT200"));

        // Financial correctness
        assertThat(response.promoDiscountMinor()).isEqualTo(expectedDiscount);
        assertThat(response.discountedNetAmountMinor()).isEqualTo(expectedNet);
        assertThat(response.netAmountMinor()).isEqualTo(rawNetMinor);
        assertThat(response.invoiceId()).isEqualTo(invoiceId);

        // Invoice: exactly 3 line items — CREDIT + PRORATION + DISCOUNT
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.List<InvoiceLineItem>> itemsCaptor =
            org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(invoiceGenerationService).generateInvoice(
            any(), any(), any(), any(), any(), any(), itemsCaptor.capture(), any());
        assertThat(itemsCaptor.getValue()).hasSize(3);

        // Snapshot stores promo code and actual monetary discount (not the percentage or face value)
        org.mockito.ArgumentCaptor<PpmChangeSnapshot> snapshotCaptor =
            org.mockito.ArgumentCaptor.forClass(PpmChangeSnapshot.class);
        verify(ppmChangeSnapshotRepositoryPort).save(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().getAppliedPromoCode()).isEqualTo("FLAT200");
        assertThat(snapshotCaptor.getValue().getPromoDiscountMinor()).isEqualTo(expectedDiscount);
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private ApplyPlanChangeCommand makeApplyRequest() {
        return new ApplyPlanChangeCommand(
            tenantId, targetPpmPlanId, "IN", BillingCycle.MONTHLY,
            "test", "tester", "https://success.test", "https://cancel.test", null);
    }

    private ApplyPlanChangeCommand makeApplyRequestWithPromo(String promoCode) {
        return new ApplyPlanChangeCommand(
            tenantId, targetPpmPlanId, "IN", BillingCycle.MONTHLY,
            "test", "tester", "https://success.test", "https://cancel.test", promoCode);
    }
}
