package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.domain.model.InitiateCheckoutCommand;
import com.company.bsmsvc.domain.model.CheckoutResult;
import com.company.bsmsvc.application.service.InvoiceGenerationService;
import com.company.bsmsvc.application.service.PaymentService;
import com.company.bsmsvc.application.service.PpmCheckoutService;
import com.company.bsmsvc.domain.port.PpmPricingService;
import com.company.bsmsvc.domain.port.PpmPromoService;
import com.company.bsmsvc.domain.port.PpmVersionService;
import com.company.bsmsvc.application.service.SubscriptionService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.InvoiceLineItemType;
import com.company.bsmsvc.domain.enums.InvoiceSource;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.domain.model.InvoiceLineItem;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.model.payment.CheckoutSessionResult;
import com.company.bsmsvc.domain.model.PpmPlanVersionResult;
import com.company.bsmsvc.domain.model.PpmResolvePriceResult;
import com.company.bsmsvc.domain.model.PpmValidatePromoResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the PPM-backed checkout flow (Phase C1).
 *
 * <p>Integration points:
 * <ul>
 *   <li>PPM Pricing Resolver — authoritative price source for this path.</li>
 *   <li>PPM Promo Validator — authoritative promo validation; BSM never
 *       re-implements date/cap/eligibility logic.</li>
 *   <li>BSM SubscriptionService — subscription creation (unchanged).</li>
 *   <li>BSM InvoiceGenerationService — invoice with PPM-resolved amounts.</li>
 *   <li>BSM PaymentService — checkout session creation (unchanged).</li>
 * </ul>
 *
 * <p>Fallback policy (per Phase C1 spec):
 * <ul>
 *   <li>Pricing failure → checkout fails. BSM never invents a price.</li>
 *   <li>Promo failure → checkout fails. BSM never silently approves a promo.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PpmCheckoutServiceImpl implements PpmCheckoutService {

    private static final long   CENTS_MULTIPLIER  = 100L;
    private static final int    INVOICE_DUE_DAYS  = 7;

    private final TenantBillingProfileService billingProfileService;
    private final PpmPricingService           ppmPricingService;
    private final PpmPromoService             ppmPromoService;
    private final PpmVersionService           ppmVersionService;
    private final SubscriptionService         subscriptionService;
    private final InvoiceGenerationService    invoiceGenerationService;
    private final PaymentService              paymentService;

    @Override
    @Transactional
    public CheckoutResult initiateCheckout(InitiateCheckoutCommand request) {
        log.info("[PpmCheckout] start tenantId={} ppmPlanId={} cycle={} region={} promo={}",
            request.tenantId(), request.ppmPlanId(),
            request.billingCycle(), request.region(),
            request.promoCode() != null ? request.promoCode() : "none");

        // ── 1. Load tenant currency ─────────────────────────────────────────
        TenantBillingProfile profile = billingProfileService.getProfile(request.tenantId());
        String currency = profile.getCurrency();

        // ── 2. Map BSM BillingCycle → PPM cycle wire value ──────────────────
        String ppmCycle = toPpmCycle(request.billingCycle());

        // ── 3. Resolve price from PPM ───────────────────────────────────────
        PpmResolvePriceResult resolved = ppmPricingService.resolvePrice(
            request.ppmPlanId(), request.region(), currency, ppmCycle);

        long resolvedAmountMinor = toMinorUnits(resolved.amount());
        log.info("[PpmCheckout] price resolved ppmPlanId={} amount={} amountMinor={} currency={}",
            request.ppmPlanId(), resolved.amount(), resolvedAmountMinor, resolved.currency());

        // ── 3b. Resolve latest plan version for grandfathering (C2) ────────────
        PpmPlanVersionResult planVersion = ppmVersionService.getLatestVersion(request.ppmPlanId());
        log.info("[PpmCheckout] version locked ppmPlanId={} versionId={} versionNo={}",
            request.ppmPlanId(), planVersion.id(), planVersion.versionNo());

        // K1: Cross-check that both PPM responses reference the same plan.
        // Guards against corrupted PPM responses that could lock price from plan A
        // against version from plan B — a data consistency violation that would
        // corrupt grandfathering for the lifetime of the subscription.
        if (!resolved.planId().equals(planVersion.planId())) {
            throw new PpmIntegrationException(
                "PPM response mismatch: price.planId=" + resolved.planId()
                    + " != version.planId=" + planVersion.planId()
                    + " for requested ppmPlanId=" + request.ppmPlanId());
        }

        // ── 4. Validate promo (if provided) ────────────────────────────────
        Long discountAmountMinor = null;
        String appliedPromoCode  = null;

        if (request.promoCode() != null && !request.promoCode().isBlank()) {
            PpmValidatePromoResult promoResult = ppmPromoService.validatePromo(
                request.promoCode(), request.ppmPlanId());

            if (!promoResult.valid()) {
                log.warn("[PpmCheckout] promo rejected code={} reason={}", request.promoCode(), promoResult.reason());
                throw new BusinessRuleViolationException(
                    "Promo code '" + request.promoCode() + "' is not applicable: " + promoResult.reason());
            }

            discountAmountMinor = calculateDiscount(
                resolvedAmountMinor, promoResult.discountType(), promoResult.discountValue());
            appliedPromoCode = promoResult.code();
            log.info("[PpmCheckout] promo applied code={} discountMinor={}", appliedPromoCode, discountAmountMinor);
        }

        // ── 5. Create subscription (C2: lock all 4 PPM identifiers) ────────────
        Subscription draft = Subscription.builder()
            .tenantId(request.tenantId())
            .billingCycle(request.billingCycle())
            .ppmPlanId(request.ppmPlanId())
            .ppmPriceId(resolved.priceId())
            .ppmPlanVersionId(planVersion.id())
            .ppmResolvedPriceMinor(resolvedAmountMinor)
            .build();

        Subscription sub = subscriptionService.createSubscription(
            draft, null, request.reason(), request.performedBy());
        log.info("[PpmCheckout] subscription created subscriptionId={} ppmPlanId={} ppmPriceId={} ppmPlanVersionId={}",
            sub.getId(), sub.getPpmPlanId(), sub.getPpmPriceId(), sub.getPpmPlanVersionId());

        // ── 6. Build invoice line items with PPM-resolved price ─────────────
        Instant periodStart = sub.getCurrentPeriodStart();
        Instant periodEnd   = sub.getCurrentPeriodEnd();
        LocalDate dueDate   = periodStart.plus(INVOICE_DUE_DAYS, ChronoUnit.DAYS)
                                         .atZone(ZoneOffset.UTC).toLocalDate();

        List<InvoiceLineItem> lineItems = new ArrayList<>();
        lineItems.add(InvoiceLineItem.builder()
            .id(UUID.randomUUID())
            .itemType(InvoiceLineItemType.SUBSCRIPTION)
            .description("Subscription — " + request.billingCycle().name().toLowerCase()
                + " (PPM resolved)")
            .quantity(1)
            .unitAmountMinor(resolvedAmountMinor)
            .amountMinor(resolvedAmountMinor)
            .createdAt(Instant.now())
            .build());

        if (discountAmountMinor != null && discountAmountMinor > 0) {
            long discountMinorNeg = -discountAmountMinor;
            lineItems.add(InvoiceLineItem.builder()
                .id(UUID.randomUUID())
                .itemType(InvoiceLineItemType.DISCOUNT)
                .description("Promo: " + appliedPromoCode)
                .quantity(1)
                .unitAmountMinor(discountMinorNeg)
                .amountMinor(discountMinorNeg)
                .createdAt(Instant.now())
                .build());
        }

        // ── 7. Generate invoice ─────────────────────────────────────────────
        PlatformInvoice invoice = invoiceGenerationService.generateInvoice(
            request.tenantId(),
            sub.getId(),
            currency,
            periodStart,
            periodEnd,
            dueDate,
            lineItems,
            InvoiceSource.MANUAL
        );
        log.info("[PpmCheckout] invoice generated invoiceId={} amountDue={}",
            invoice.getId(), invoice.getAmountDue());

        // ── 8. Create payment checkout session ──────────────────────────────
        CheckoutSessionResult session = paymentService.createCheckoutSession(
            request.tenantId(), invoice.getId(), request.successUrl(), request.cancelUrl());
        log.info("[PpmCheckout] checkout session created invoiceId={} sessionId={}",
            invoice.getId(), session.sessionId());

        return new CheckoutResult(
            sub.getId(),
            invoice.getId(),
            session.checkoutUrl(),
            session.sessionId(),
            resolvedAmountMinor,
            currency,
            discountAmountMinor,
            planVersion.id()
        );
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String toPpmCycle(BillingCycle cycle) {
        return switch (cycle) {
            case MONTHLY -> "monthly";
            case YEARLY  -> "annual";
        };
    }

    /**
     * Converts a major-unit BigDecimal to minor units (long).
     * Example: 999.00 INR → 99900 paise.
     */
    private static long toMinorUnits(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(CENTS_MULTIPLIER))
                     .setScale(0, RoundingMode.HALF_UP)
                     .longValueExact();
    }

    /**
     * Calculates the discount amount in minor units.
     *
     * @param baseAmountMinor resolved price in minor units
     * @param discountType    PPM wire value: {@code "percentage"} or {@code "flat"}
     * @param discountValue   PPM discount value
     * @return discount amount in minor units (always positive; applied as negative on invoice)
     */
    private static long calculateDiscount(long baseAmountMinor, String discountType, BigDecimal discountValue) {
        if (discountType == null || discountValue == null) {
            return 0L;
        }
        long raw = switch (discountType) {
            case "percentage" -> BigDecimal.valueOf(baseAmountMinor)
                .multiply(discountValue)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValue();
            case "flat" -> toMinorUnits(discountValue);
            default -> throw new BusinessRuleViolationException(
                "Unknown PPM discount type: " + discountType);
        };
        // Floor: discount can never exceed the price (prevents negative invoice totals).
        return Math.min(raw, baseAmountMinor);
    }
}
