package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.application.service.InvoiceGenerationService;
import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.application.service.SubscriptionService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.application.service.TenantOnboardingService;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.InvoiceLineItemType;
import com.company.bsmsvc.domain.enums.InvoiceSource;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.exception.PpmIntegrationException;
import com.company.bsmsvc.domain.exception.TenantBillingProfileNotFoundException;
import com.company.bsmsvc.domain.exception.TrialAlreadyConsumedException;
import com.company.bsmsvc.domain.model.InvoiceLineItem;
import com.company.bsmsvc.domain.model.OnboardTenantCommand;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.PpmDefaultTrialResult;
import com.company.bsmsvc.domain.model.PpmResolvePriceResult;
import com.company.bsmsvc.domain.model.PpmVersionMetaResult;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.TrialPolicy;
import com.company.bsmsvc.domain.port.DefaultTrialPlanPort;
import com.company.bsmsvc.domain.port.PlanVersionMetaPort;
import com.company.bsmsvc.domain.port.PpmPricingService;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Onboards a tenant into a BSM subscription — TRIALING only if the resolved plan
 * is the free/trial plan, ACTIVE immediately for paid plans (Starter/Growth/Enterprise).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantOnboardingServiceImpl implements TenantOnboardingService {

    private static final long INVOICE_DUE_DAYS = 7;
    private static final long CENTS_MULTIPLIER = 100L;

    /**
     * The platform's only currently-priced currency/region (see ppm_plan_prices —
     * every seeded plan price is INDIA/INR). Used as the default for
     * auto-created TenantBillingProfiles and PPM price resolution until more
     * regions/currencies are actually priced.
     */
    private static final String DEFAULT_CURRENCY = "INR";
    private static final String DEFAULT_REGION = "INDIA";

    private final SubscriptionRepositoryPort subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final DefaultTrialPlanPort defaultTrialPlanPort;
    private final PlanVersionMetaPort planVersionMetaPort;
    private final InvoiceGenerationService invoiceGenerationService;
    private final InvoiceService invoiceService;
    private final TenantBillingProfileService tenantBillingProfileService;
    private final PpmPricingService ppmPricingService;
    private final TrialPolicy trialPolicy;

    @Override
    @Transactional
    public void onboard(OnboardTenantCommand event) {
        UUID tenantId = event.tenantId();
        log.info("[TenantOnboarding] onboard received tenantId={} ppmPlanId={} billingCycle={}",
            tenantId, event.ppmPlanId(), event.billingCycle());

        // Idempotency: skip if subscription already exists
        Optional<Subscription> existing = subscriptionRepository.findCurrentByTenantId(tenantId);
        if (existing.isPresent()) {
            log.info("[TenantOnboarding] Subscription already exists for tenantId={} — skipping", tenantId);
            return;
        }

        UUID resolvedPlanId;
        UUID resolvedPlanVersionId;
        boolean isTrialPlan;
        if (event.ppmPlanVersionId() != null && event.ppmPlanId() != null) {
            resolvedPlanId = event.ppmPlanId();
            resolvedPlanVersionId = event.ppmPlanVersionId();
            log.info("[TenantOnboarding] Using pre-resolved PPM plan tenantId={} ppmPlanVersionId={}",
                tenantId, resolvedPlanVersionId);
            isTrialPlan = resolveIsTrialPlan(tenantId, resolvedPlanVersionId);
        } else {
            // No plan was pre-selected at signup — PPM's default-trial plan is used,
            // which is by definition the trial/free plan.
            PpmDefaultTrialResult trial;
            try {
                trial = defaultTrialPlanPort.getDefaultTrialPlan();
            } catch (PpmIntegrationException e) {
                // Fail-fast: let the host's messaging adapter retry after backoff.
                // Prevents silent onboarding failures when PPM is temporarily unavailable.
                throw new IllegalStateException(
                    "Failed to resolve trial plan from PPM for tenantId=" + tenantId
                    + ": " + e.getMessage(), e);
            }
            resolvedPlanId = trial.planId();
            resolvedPlanVersionId = trial.planVersionId();
            isTrialPlan = true;
        }

        BillingCycle billingCycle = parseBillingCycle(event.billingCycle());
        Instant now = Instant.now();
        int trialDays = isTrialPlan ? trialPolicy.defaultTrialDays() : 0;
        Instant trialEnd = trialDays > 0 ? now.plus(trialDays, ChronoUnit.DAYS) : null;

        // Resolve price up front (not just for the invoice) so ppmPriceId is stamped
        // onto the subscription itself — frontends resolve pricing details via
        // GET /api/v1/ppm/prices/{ppmPriceId} using exactly that field.
        PriceInfo priceInfo = isTrialPlan
            ? null
            : resolvePriceInfo(tenantId, resolvedPlanId, billingCycle, event.region());

        Subscription draft = Subscription.builder()
            .id(UUID.randomUUID())
            .tenantId(tenantId)
            .ppmPlanVersionId(resolvedPlanVersionId)
            .ppmPlanId(resolvedPlanId)
            .ppmPriceId(priceInfo != null ? priceInfo.priceId() : null)
            .ppmResolvedPriceMinor(priceInfo != null ? priceInfo.amountMinor() : null)
            .billingCycle(billingCycle)
            .status(trialEnd != null ? SubscriptionStatus.TRIALING : SubscriptionStatus.ACTIVE)
            .currentPeriodStart(now)
            .currentPeriodEnd(trialEnd != null ? trialEnd : now)
            .trialEndsAt(trialEnd)
            .createdAt(now)
            .updatedAt(now)
            .build();

        try {
            // SubscriptionServiceImpl recomputes status/currentPeriodEnd/trialEndsAt from
            // this trialDays argument — the draft's own values above are informational only.
            Subscription created = subscriptionService.createSubscription(
                draft, trialDays > 0 ? trialDays : null, "Tenant onboarding", "SYSTEM");
            log.info("[TenantOnboarding] Created subscription for tenantId={} ppmPlanVersionId={} isTrialPlan={} trialEnd={}",
                tenantId, resolvedPlanVersionId, isTrialPlan, trialEnd);

            generateFirstInvoice(tenantId, created, isTrialPlan, priceInfo, event.sourceChannel());
        } catch (TrialAlreadyConsumedException e) {
            log.warn("[TenantOnboarding] Trial already consumed for tenantId={} — idempotent skip (no second trial granted)", tenantId);
        } catch (BusinessRuleViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("already has an active subscription")) {
                log.info("[TenantOnboarding] Concurrent creation detected for tenantId={} — idempotent skip", tenantId);
            } else {
                throw e;
            }
        }
    }

    /**
     * A trial period is only appropriate for the platform's free/trial plan — paid
     * plans (Starter/Growth/Enterprise) should go straight to an ACTIVE subscription.
     * Asks PPM-SVC for the authoritative tier of the resolved plan version rather than
     * trusting the wire event's {@code planTier} field, whose semantics vary by producer.
     */
    private boolean resolveIsTrialPlan(UUID tenantId, UUID ppmPlanVersionId) {
        try {
            PpmVersionMetaResult meta = planVersionMetaPort.getVersionMeta(ppmPlanVersionId);
            boolean trial = "trial".equalsIgnoreCase(meta.tier()) || "FREE".equalsIgnoreCase(meta.planCode());
            log.info("[TenantOnboarding] Resolved plan tier tenantId={} ppmPlanVersionId={} tier={} planCode={} isTrialPlan={}",
                tenantId, ppmPlanVersionId, meta.tier(), meta.planCode(), trial);
            return trial;
        } catch (PpmIntegrationException e) {
            // Fail-fast rather than silently granting (or denying) a trial based on a
            // guess — let the host's messaging adapter retry once PPM is reachable again.
            throw new IllegalStateException(
                "Failed to resolve plan tier from PPM for tenantId=" + tenantId
                + " ppmPlanVersionId=" + ppmPlanVersionId + ": " + e.getMessage(), e);
        }
    }

    /** Small holder for a resolved PPM price, so it's fetched once and reused
     *  for both the subscription's ppmPriceId and the first invoice's amount. */
    private record PriceInfo(UUID priceId, long amountMinor, String currency) {}

    private PriceInfo resolvePriceInfo(UUID tenantId, UUID ppmPlanId, BillingCycle billingCycle, String region) {
        ensureBillingProfile(tenantId);
        String priceRegion = region != null ? region : DEFAULT_REGION;
        PpmResolvePriceResult resolved = ppmPricingService.resolvePrice(
            ppmPlanId, priceRegion, DEFAULT_CURRENCY, toPpmCycle(billingCycle));
        return new PriceInfo(resolved.priceId(), toMinorUnits(resolved.amount()), resolved.currency());
    }

    /**
     * Every tenant should have a first invoice — whether self-signed-up via
     * REG-SVC (payment already collected through REG-SVC's own Stripe/Razorpay
     * checkout before the tenant was created) or provisioned by an admin via
     * SUP-SVC (no payment evidence — invoice is left OPEN for reconciliation).
     * The free/trial plan has no price at all, so its invoice is a $0 record,
     * marked PAID immediately regardless of channel since nothing is owed.
     */
    private void generateFirstInvoice(UUID tenantId, Subscription subscription,
                                       boolean isTrialPlan, PriceInfo priceInfo, String sourceChannel) {
        long amountMinor = isTrialPlan ? 0L : priceInfo.amountMinor();
        String currency = isTrialPlan ? DEFAULT_CURRENCY : priceInfo.currency();

        InvoiceLineItem lineItem = InvoiceLineItem.builder()
            .id(UUID.randomUUID())
            .itemType(InvoiceLineItemType.SUBSCRIPTION)
            .description(isTrialPlan
                ? "Free plan — no charge"
                : "Subscription — " + subscription.getBillingCycle().name().toLowerCase() + " (initial)")
            .quantity(1)
            .unitAmountMinor(amountMinor)
            .amountMinor(amountMinor)
            .createdAt(Instant.now())
            .build();

        LocalDate dueDate = subscription.getCurrentPeriodStart()
            .plus(INVOICE_DUE_DAYS, ChronoUnit.DAYS).atZone(ZoneOffset.UTC).toLocalDate();

        PlatformInvoice invoice = invoiceGenerationService.generateInvoice(
            tenantId, subscription.getId(), currency,
            subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd(),
            dueDate, List.of(lineItem), InvoiceSource.MANUAL);

        boolean alreadyPaid = amountMinor == 0L || "SELF_SIGNUP".equalsIgnoreCase(sourceChannel);
        if (alreadyPaid) {
            invoiceService.applyPayment(invoice.getId(), null, null);
        }

        log.info("[TenantOnboarding] Generated first invoice tenantId={} invoiceId={} amountMinor={} "
                + "currency={} sourceChannel={} status={}",
            tenantId, invoice.getId(), amountMinor, currency, sourceChannel, alreadyPaid ? "PAID" : "OPEN");
    }

    private void ensureBillingProfile(UUID tenantId) {
        try {
            tenantBillingProfileService.getProfile(tenantId);
        } catch (TenantBillingProfileNotFoundException e) {
            tenantBillingProfileService.createProfile(tenantId, PaymentProvider.RAZORPAY, null, DEFAULT_CURRENCY);
            log.info("[TenantOnboarding] Auto-created billing profile tenantId={} currency={}",
                tenantId, DEFAULT_CURRENCY);
        }
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

    private BillingCycle parseBillingCycle(String raw) {
        if (raw == null) return BillingCycle.MONTHLY;
        try {
            return BillingCycle.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BillingCycle.MONTHLY;
        }
    }
}
