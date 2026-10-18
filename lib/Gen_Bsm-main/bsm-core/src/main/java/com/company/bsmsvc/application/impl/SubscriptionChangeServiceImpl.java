package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.domain.model.ApplyPlanChangeCommand;
import com.company.bsmsvc.domain.model.PreviewPlanChangeCommand;
import com.company.bsmsvc.domain.model.PlanChangeApplyResult;
import com.company.bsmsvc.domain.model.PlanChangePreviewResult;
import com.company.bsmsvc.application.service.InvoiceGenerationService;
import com.company.bsmsvc.application.service.PaymentService;
import com.company.bsmsvc.domain.port.PpmPricingService;
import com.company.bsmsvc.domain.port.PpmPromoService;
import com.company.bsmsvc.domain.port.PpmVersionService;
import com.company.bsmsvc.application.service.SubscriptionChangeService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.ActorType;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.InvoiceLineItemType;
import com.company.bsmsvc.domain.enums.InvoiceSource;
import com.company.bsmsvc.domain.enums.PpmPlanChangeType;
import com.company.bsmsvc.domain.enums.SubscriptionEventType;
import com.company.bsmsvc.domain.enums.SubscriptionHistoryAction;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.domain.exception.SubscriptionNotFoundException;
import com.company.bsmsvc.domain.model.InvoiceLineItem;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.PpmChangeSnapshot;
import com.company.bsmsvc.domain.model.PpmProrationResult;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.SubscriptionEvent;
import com.company.bsmsvc.domain.model.SubscriptionHistory;
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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionChangeServiceImpl implements SubscriptionChangeService {

    private static final long CENTS_MULTIPLIER = 100L;
    private static final int  INVOICE_DUE_DAYS = 7;

    private final SubscriptionRepositoryPort        subscriptionRepositoryPort;
    private final SubscriptionHistoryRepositoryPort subscriptionHistoryRepositoryPort;
    private final SubscriptionEventRepositoryPort   subscriptionEventRepositoryPort;
    private final PpmChangeSnapshotRepositoryPort   ppmChangeSnapshotRepositoryPort;
    private final TenantBillingProfileService       tenantBillingProfileService;
    private final TenantOwnershipValidator          tenantOwnershipValidator;
    private final PpmPricingService                 ppmPricingService;
    private final PpmVersionService                 ppmVersionService;
    private final PpmPromoService                   ppmPromoService;
    private final PpmProrationEngine                prorationEngine;
    private final InvoiceGenerationService          invoiceGenerationService;
    private final PaymentService                    paymentService;

    // ── previewChange ─────────────────────────────────────────────────────────

    @Override
    public PlanChangePreviewResult previewChange(UUID subscriptionId, PreviewPlanChangeCommand request) {
        Subscription sub = loadAndValidate(subscriptionId, request.tenantId());

        TenantBillingProfile profile = tenantBillingProfileService.getProfile(request.tenantId());
        String currency = profile.getCurrency();

        String ppmCycle = toPpmCycle(request.cycle());
        PpmResolvePriceResult resolved = ppmPricingService.resolvePrice(
            request.targetPpmPlanId(), request.region(), currency, ppmCycle);
        PpmPlanVersionResult planVersion = ppmVersionService.getLatestVersion(request.targetPpmPlanId());

        k1CrossCheck(resolved, planVersion, request.targetPpmPlanId());

        long targetMinor = toMinorUnits(resolved.amount());

        // Validate promo early — fail before proration if code is invalid
        PpmValidatePromoResult validatedPromo =
            validateAndGetPromo(request.promoCode(), request.targetPpmPlanId());

        PpmProrationResult proration = prorationEngine.calculate(
            sub.getPpmResolvedPriceMinor(),
            targetMinor,
            sub.getCurrentPeriodStart(),
            sub.getCurrentPeriodEnd(),
            Instant.now()
        );

        long discountMinor = 0L;
        if (proration.netAmountMinor() > 0 && validatedPromo != null) {
            discountMinor = computeDiscountAmount(validatedPromo, proration.chargeAmountMinor());
        }
        long discountedNetMinor =
            Math.max(0L, proration.chargeAmountMinor() - discountMinor) - proration.creditAmountMinor();

        log.info("[SubscriptionChange] preview subscriptionId={} changeType={} net={} discount={}",
            subscriptionId, proration.changeType(), proration.netAmountMinor(), discountMinor);

        return new PlanChangePreviewResult(
            sub.getId(),
            sub.getPpmPlanId(),
            sub.getPpmResolvedPriceMinor(),
            request.targetPpmPlanId(),
            resolved.priceId(),
            planVersion.id(),
            targetMinor,
            proration.creditAmountMinor(),
            proration.chargeAmountMinor(),
            proration.netAmountMinor(),
            currency,
            proration.changeType(),
            discountMinor,
            discountedNetMinor
        );
    }

    // ── applyChange ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public PlanChangeApplyResult applyChange(UUID subscriptionId, ApplyPlanChangeCommand request) {
        log.info("[SubscriptionChange] apply start subscriptionId={} tenantId={} targetPpmPlanId={}",
            subscriptionId, request.tenantId(), request.targetPpmPlanId());

        // 1. Validate subscription
        Subscription sub = loadAndValidate(subscriptionId, request.tenantId());

        // 2. Currency
        TenantBillingProfile profile = tenantBillingProfileService.getProfile(request.tenantId());
        String currency = profile.getCurrency();

        // 3. Resolve target PPM identifiers — fail-closed
        String ppmCycle = toPpmCycle(request.cycle());
        PpmResolvePriceResult resolved = ppmPricingService.resolvePrice(
            request.targetPpmPlanId(), request.region(), currency, ppmCycle);
        PpmPlanVersionResult planVersion = ppmVersionService.getLatestVersion(request.targetPpmPlanId());

        // 4. K1 cross-check
        k1CrossCheck(resolved, planVersion, request.targetPpmPlanId());

        long targetMinor = toMinorUnits(resolved.amount());

        // 5. Guard: same plan+price means no-op
        if (request.targetPpmPlanId().equals(sub.getPpmPlanId())
            && resolved.priceId().equals(sub.getPpmPriceId())) {
            throw new BusinessRuleViolationException(
                "Target PPM plan is identical to the current plan; no change applied");
        }

        // 6. Validate promo — fail before any writes if code is invalid (PC-3)
        PpmValidatePromoResult validatedPromo =
            validateAndGetPromo(request.promoCode(), request.targetPpmPlanId());

        // 7. Proration
        Instant now = Instant.now();
        PpmProrationResult proration = prorationEngine.calculate(
            sub.getPpmResolvedPriceMinor(),
            targetMinor,
            sub.getCurrentPeriodStart(),
            sub.getCurrentPeriodEnd(),
            now
        );

        // 8. Discount — only for upgrades (net > 0); silently ignored for downgrades (PC-8)
        long discountMinor = 0L;
        if (proration.netAmountMinor() > 0 && validatedPromo != null) {
            discountMinor = computeDiscountAmount(validatedPromo, proration.chargeAmountMinor());
        }
        long discountedChargeMinor = Math.max(0L, proration.chargeAmountMinor() - discountMinor);
        long discountedNetMinor    = discountedChargeMinor - proration.creditAmountMinor();

        // 8. Invoice — generated BEFORE subscription update so a DB failure during invoice
        //    creation never leaves the subscription in a mutated state.
        UUID invoiceId = null;
        if (discountedNetMinor > 0) {
            PlatformInvoice invoice = generateAdjustmentInvoice(
                sub, proration, resolved, currency, now, discountMinor);
            invoiceId = invoice.getId();
            log.info("[SubscriptionChange] upgrade invoice generated invoiceId={}", invoiceId);
        }

        // 8. Update subscription PPM fields (grandfathering — new price locked in)
        Subscription updated = sub.toBuilder()
            .ppmPlanId(request.targetPpmPlanId())
            .ppmPriceId(resolved.priceId())
            .ppmPlanVersionId(planVersion.id())
            .ppmResolvedPriceMinor(targetMinor)
            .build();
        subscriptionRepositoryPort.save(updated);

        // 9. Snapshot (append-only)
        PpmChangeSnapshot snapshot = PpmChangeSnapshot.builder()
            .id(UUID.randomUUID())
            .subscriptionId(sub.getId())
            .tenantId(sub.getTenantId())
            .changeType(proration.changeType())
            .fromPpmPlanId(sub.getPpmPlanId())
            .fromPpmPriceId(sub.getPpmPriceId())
            .fromPpmPlanVersionId(sub.getPpmPlanVersionId())
            .fromPpmResolvedPriceMinor(sub.getPpmResolvedPriceMinor())
            .toPpmPlanId(request.targetPpmPlanId())
            .toPpmPriceId(resolved.priceId())
            .toPpmPlanVersionId(planVersion.id())
            .toPpmResolvedPriceMinor(targetMinor)
            .prorationCreditMinor(proration.creditAmountMinor())
            .prorationChargeMinor(proration.chargeAmountMinor())
            .prorationNetMinor(proration.netAmountMinor())
            .invoiceId(invoiceId)
            .appliedPromoCode(discountMinor > 0 ? request.promoCode() : null)
            .promoDiscountMinor(discountMinor > 0 ? discountMinor : null)
            .changedAt(now)
            .changedBy(request.performedBy())
            .reason(request.reason())
            .build();
        ppmChangeSnapshotRepositoryPort.save(snapshot);

        // 10. Audit trail
        saveHistory(updated, sub.getPpmPlanId(), request.reason(), request.performedBy(), now);
        saveEvent(updated, proration.changeType(), request.targetPpmPlanId(), planVersion.id(), now);

        // 11. Payment provider checkout session — intentionally LAST, after all DB writes.
        //     Placing this after every DB write minimises the window in which the transaction
        //     could roll back and leave an orphaned provider session (same trade-off as
        //     PpmCheckoutServiceImpl; provider sessions expire so the orphan risk is acceptable).
        CheckoutSessionResult session = null;
        if (invoiceId != null) {
            session = paymentService.createCheckoutSession(
                request.tenantId(), invoiceId, request.successUrl(), request.cancelUrl());
            log.info("[SubscriptionChange] checkout session created invoiceId={} sessionId={}",
                invoiceId, session.sessionId());
        }

        log.info("[SubscriptionChange] apply done subscriptionId={} changeType={} invoiceId={}",
            subscriptionId, proration.changeType(), invoiceId);

        return PlanChangeApplyResult.builder()
            .subscriptionId(sub.getId())
            .invoiceId(invoiceId)
            .checkoutUrl(session != null ? session.checkoutUrl() : null)
            .sessionId(session != null ? session.sessionId() : null)
            .netAmountMinor(proration.netAmountMinor())
            .currency(currency)
            .changeType(proration.changeType())
            .ppmPlanVersionId(planVersion.id())
            .promoDiscountMinor(discountMinor)
            .discountedNetAmountMinor(discountedNetMinor)
            .build();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Subscription loadAndValidate(UUID subscriptionId, UUID tenantId) {
        Subscription sub = subscriptionRepositoryPort.findById(subscriptionId)
            .orElseThrow(() -> new SubscriptionNotFoundException(
                "Subscription not found: " + subscriptionId));
        tenantOwnershipValidator.validate(sub, tenantId);
        if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                "Subscription must be ACTIVE to apply a plan change; current status: " + sub.getStatus());
        }
        if (sub.getPpmPlanId() == null) {
            throw new BusinessRuleViolationException(
                "Subscription " + subscriptionId + " was not created through PPM checkout; "
                    + "use the native plan change endpoint for BSM-native subscriptions");
        }
        return sub;
    }

    private static void k1CrossCheck(
        PpmResolvePriceResult resolved,
        PpmPlanVersionResult planVersion,
        UUID requestedPpmPlanId
    ) {
        if (!resolved.planId().equals(planVersion.planId())) {
            throw new PpmIntegrationException(
                "PPM response mismatch: price.planId=" + resolved.planId()
                    + " != version.planId=" + planVersion.planId()
                    + " for requested ppmPlanId=" + requestedPpmPlanId);
        }
    }

    private PlatformInvoice generateAdjustmentInvoice(
        Subscription sub,
        PpmProrationResult proration,
        PpmResolvePriceResult resolved,
        String currency,
        Instant now,
        long discountMinor
    ) {
        Instant periodStart = sub.getCurrentPeriodStart();
        Instant periodEnd   = sub.getCurrentPeriodEnd();
        LocalDate dueDate   = now.plus(INVOICE_DUE_DAYS, ChronoUnit.DAYS)
            .atZone(ZoneOffset.UTC).toLocalDate();

        java.util.List<InvoiceLineItem> items = new java.util.ArrayList<>();
        items.add(InvoiceLineItem.builder()
            .id(UUID.randomUUID())
            .itemType(InvoiceLineItemType.CREDIT)
            .description("Proration credit — remaining period on current plan")
            .quantity(1)
            .unitAmountMinor(-proration.creditAmountMinor())
            .amountMinor(-proration.creditAmountMinor())
            .createdAt(now)
            .build());
        items.add(InvoiceLineItem.builder()
            .id(UUID.randomUUID())
            .itemType(InvoiceLineItemType.PRORATION)
            .description("Proration charge — remaining period on new plan")
            .quantity(1)
            .unitAmountMinor(proration.chargeAmountMinor())
            .amountMinor(proration.chargeAmountMinor())
            .createdAt(now)
            .build());
        if (discountMinor > 0) {
            items.add(InvoiceLineItem.builder()
                .id(UUID.randomUUID())
                .itemType(InvoiceLineItemType.DISCOUNT)
                .description("Promo code discount")
                .quantity(1)
                .unitAmountMinor(-discountMinor)
                .amountMinor(-discountMinor)
                .createdAt(now)
                .build());
        }

        return invoiceGenerationService.generateInvoice(
            sub.getTenantId(), sub.getId(), currency,
            periodStart, periodEnd, dueDate, List.copyOf(items), InvoiceSource.UPGRADE);
    }

    private PpmValidatePromoResult validateAndGetPromo(String promoCode, UUID targetPpmPlanId) {
        if (promoCode == null || promoCode.isBlank()) return null;
        PpmValidatePromoResult promo = ppmPromoService.validatePromo(promoCode, targetPpmPlanId);
        if (!promo.valid()) {
            throw new BusinessRuleViolationException(
                "Promo code '" + promoCode + "' is not valid: " + promo.reason());
        }
        return promo;
    }

    private long computeDiscountAmount(PpmValidatePromoResult promo, long chargeAmountMinor) {
        long rawDiscount;
        if ("percentage".equals(promo.discountType())) {
            rawDiscount = BigDecimal.valueOf(chargeAmountMinor)
                .multiply(promo.discountValue())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValueExact();
        } else {
            rawDiscount = toMinorUnits(promo.discountValue());
        }
        return Math.min(rawDiscount, chargeAmountMinor);
    }

    private void saveHistory(Subscription sub, UUID fromPpmPlanId, String reason,
                             String performedBy, Instant now) {
        subscriptionHistoryRepositoryPort.save(
            SubscriptionHistory.builder()
                .id(UUID.randomUUID())
                .subscriptionId(sub.getId())
                .tenantId(sub.getTenantId())
                .action(SubscriptionHistoryAction.SUBSCRIPTION_PPM_PLAN_CHANGED)
                .reason(reason)
                .performedBy(performedBy)
                .actorType(ActorType.USER)
                .occurredAt(now)
                .build());
    }

    private void saveEvent(Subscription sub, PpmPlanChangeType changeType,
                           UUID targetPpmPlanId, UUID targetPpmPlanVersionId, Instant now) {
        subscriptionEventRepositoryPort.save(
            SubscriptionEvent.builder()
                .id(UUID.randomUUID())
                .subscriptionId(sub.getId())
                .tenantId(sub.getTenantId())
                .eventType(SubscriptionEventType.SUBSCRIPTION_PPM_PLAN_CHANGED)
                .payload(Map.of(
                    "changeType", changeType.name(),
                    "toPpmPlanId", sub.getPpmPlanId().toString(),
                    "toPpmPlanVersionId", targetPpmPlanVersionId.toString(),
                    "toPpmResolvedPriceMinor", sub.getPpmResolvedPriceMinor()
                ))
                .eventVersion(1)
                .actorType(ActorType.USER)
                .occurredAt(now)
                .createdAt(now)
                .build());
    }

    private static String toPpmCycle(BillingCycle cycle) {
        return switch (cycle) {
            case MONTHLY -> "monthly";
            case YEARLY  -> "annual";
        };
    }

    private static long toMinorUnits(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(CENTS_MULTIPLIER))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact();
    }
}
