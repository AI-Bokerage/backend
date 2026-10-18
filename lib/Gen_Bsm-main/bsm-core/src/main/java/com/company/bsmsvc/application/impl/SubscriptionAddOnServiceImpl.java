package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.domain.model.AddOnPurchaseResult;
import com.company.bsmsvc.domain.model.PurchaseAddOnCommand;
import com.company.bsmsvc.application.service.InvoiceGenerationService;
import com.company.bsmsvc.application.service.PaymentService;
import com.company.bsmsvc.domain.port.PpmAddOnPricingService;
import com.company.bsmsvc.application.service.SubscriptionAddOnService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.ActorType;
import com.company.bsmsvc.domain.enums.InvoiceLineItemType;
import com.company.bsmsvc.domain.enums.InvoiceSource;
import com.company.bsmsvc.domain.enums.SubscriptionHistoryAction;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.exception.AddOnAlreadyPurchasedException;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.exception.SubscriptionAddOnNotFoundException;
import com.company.bsmsvc.domain.exception.SubscriptionNotFoundException;
import com.company.bsmsvc.domain.model.InvoiceLineItem;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.SubscriptionAddOn;
import com.company.bsmsvc.domain.model.SubscriptionHistory;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.model.payment.CheckoutSessionResult;
import com.company.bsmsvc.domain.port.SubscriptionAddOnRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.service.TenantOwnershipValidator;
import com.company.bsmsvc.domain.model.PpmResolvedAddOnPriceResult;
import com.company.bsmsvc.domain.port.AddOnEventPublisherPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionAddOnServiceImpl implements SubscriptionAddOnService {

    private static final long CENTS_MULTIPLIER = 100L;
    private static final int  INVOICE_DUE_DAYS = 7;

    private final SubscriptionRepositoryPort      subscriptionRepositoryPort;
    private final SubscriptionAddOnRepositoryPort subscriptionAddOnRepositoryPort;
    private final SubscriptionHistoryRepositoryPort subscriptionHistoryRepositoryPort;
    private final TenantBillingProfileService     tenantBillingProfileService;
    private final TenantOwnershipValidator        tenantOwnershipValidator;
    private final PpmAddOnPricingService          ppmAddOnPricingService;
    private final InvoiceGenerationService        invoiceGenerationService;
    private final PaymentService                  paymentService;
    private final AddOnEventPublisherPort         addOnEventPublisher;

    // ── purchaseAddOn ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AddOnPurchaseResult purchaseAddOn(UUID subscriptionId, PurchaseAddOnCommand request) {
        log.info("[SubscriptionAddOnService] purchase start subscriptionId={} ppmAddOnId={} tenantId={}",
            subscriptionId, request.ppmAddOnId(), request.tenantId());

        Instant now = Instant.now();

        // 1. Load + validate subscription
        Subscription sub = subscriptionRepositoryPort.findById(subscriptionId)
            .orElseThrow(() -> new SubscriptionNotFoundException(
                "Subscription not found: " + subscriptionId));
        tenantOwnershipValidator.validate(sub, request.tenantId());
        if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                "Subscription must be ACTIVE to purchase an add-on; current status: " + sub.getStatus());
        }

        // 2. Duplicate guard — at most one active add-on per (subscription, ppmAddOnId)
        if (subscriptionAddOnRepositoryPort.existsActive(subscriptionId, request.ppmAddOnId())) {
            throw new AddOnAlreadyPurchasedException(
                "Add-on " + request.ppmAddOnId() + " is already active on subscription " + subscriptionId);
        }

        // 3. Currency from tenant billing profile
        TenantBillingProfile profile = tenantBillingProfileService.getProfile(request.tenantId());
        String currency = profile.getCurrency();

        // 4. Resolve add-on price from PPM — fail-closed
        PpmResolvedAddOnPriceResult resolved = ppmAddOnPricingService.resolveActivePrice(
            request.ppmAddOnId(), request.region(), currency, request.cycle());
        long resolvedPriceMinor = toMinorUnits(resolved.amount());

        // 5. Persist locked SubscriptionAddOn
        SubscriptionAddOn addOn = SubscriptionAddOn.builder()
            .id(UUID.randomUUID())
            .subscriptionId(subscriptionId)
            .tenantId(request.tenantId())
            .ppmAddOnId(request.ppmAddOnId())
            .ppmAddOnPriceId(resolved.priceId())
            .ppmResolvedPriceMinor(resolvedPriceMinor)
            .active(true)
            .createdAt(now)
            .createdBy(request.performedBy())
            .build();
        SubscriptionAddOn saved = subscriptionAddOnRepositoryPort.save(addOn);
        log.info("[SubscriptionAddOnService] add-on locked subscriptionAddOnId={} priceMinor={}",
            saved.getId(), resolvedPriceMinor);

        // 6. Generate invoice (full add-on price — no proration for initial purchase)
        LocalDate dueDate = now.plus(INVOICE_DUE_DAYS, ChronoUnit.DAYS)
            .atZone(ZoneOffset.UTC).toLocalDate();
        List<InvoiceLineItem> items = List.of(
            InvoiceLineItem.builder()
                .id(UUID.randomUUID())
                .itemType(InvoiceLineItemType.ADDON)
                .description("PPM add-on: " + request.ppmAddOnId())
                .quantity(1)
                .unitAmountMinor(resolvedPriceMinor)
                .amountMinor(resolvedPriceMinor)
                .createdAt(now)
                .build()
        );
        PlatformInvoice invoice = invoiceGenerationService.generateInvoice(
            request.tenantId(), subscriptionId, currency,
            sub.getCurrentPeriodStart(), sub.getCurrentPeriodEnd(), dueDate,
            items, InvoiceSource.ADDON_PURCHASE);
        log.info("[SubscriptionAddOnService] invoice generated invoiceId={}", invoice.getId());

        // 7. Audit history
        subscriptionHistoryRepositoryPort.save(
            SubscriptionHistory.builder()
                .id(UUID.randomUUID())
                .subscriptionId(subscriptionId)
                .tenantId(request.tenantId())
                .action(SubscriptionHistoryAction.SUBSCRIPTION_ADDON_PURCHASED)
                .performedBy(request.performedBy())
                .actorType(ActorType.USER)
                .occurredAt(now)
                .build());

        // 7b. Publish add-on activated event — after all DB writes, before the
        // external payment call, mirroring SubscriptionServiceImpl's convention.
        addOnEventPublisher.publishActivated(saved);

        // 8. Payment provider checkout session — intentionally LAST, after all DB writes.
        CheckoutSessionResult session = paymentService.createCheckoutSession(
            request.tenantId(), invoice.getId(), request.successUrl(), request.cancelUrl());
        log.info("[SubscriptionAddOnService] checkout session created invoiceId={} sessionId={}",
            invoice.getId(), session.sessionId());

        return AddOnPurchaseResult.builder()
            .subscriptionAddOnId(saved.getId())
            .invoiceId(invoice.getId())
            .checkoutUrl(session.checkoutUrl())
            .sessionId(session.sessionId())
            .ppmAddOnId(request.ppmAddOnId())
            .ppmAddOnPriceId(resolved.priceId())
            .ppmResolvedPriceMinor(resolvedPriceMinor)
            .currency(currency)
            .build();
    }

    // ── listAddOns ────────────────────────────────────────────────────────────

    @Override
    public List<SubscriptionAddOn> listAddOns(UUID subscriptionId, UUID tenantId) {
        Subscription sub = subscriptionRepositoryPort.findById(subscriptionId)
            .orElseThrow(() -> new SubscriptionNotFoundException(
                "Subscription not found: " + subscriptionId));
        tenantOwnershipValidator.validate(sub, tenantId);
        return subscriptionAddOnRepositoryPort.findActiveBySubscriptionId(subscriptionId);
    }

    // ── removeAddOn ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void removeAddOn(UUID subscriptionId, UUID ppmAddOnId, UUID tenantId, String performedBy) {
        log.info("[SubscriptionAddOnService] remove start subscriptionId={} ppmAddOnId={} tenantId={}",
            subscriptionId, ppmAddOnId, tenantId);

        Subscription sub = subscriptionRepositoryPort.findById(subscriptionId)
            .orElseThrow(() -> new SubscriptionNotFoundException(
                "Subscription not found: " + subscriptionId));
        tenantOwnershipValidator.validate(sub, tenantId);

        SubscriptionAddOn addOn = subscriptionAddOnRepositoryPort
            .findActiveBySubscriptionIdAndPpmAddOnId(subscriptionId, ppmAddOnId)
            .orElseThrow(() -> new SubscriptionAddOnNotFoundException(
                "No active add-on " + ppmAddOnId + " found on subscription " + subscriptionId));

        // Soft-remove — sets active=false so future renewals skip this add-on
        SubscriptionAddOn deactivated = subscriptionAddOnRepositoryPort.save(
            addOn.toBuilder().active(false).build());

        // Audit
        subscriptionHistoryRepositoryPort.save(
            SubscriptionHistory.builder()
                .id(UUID.randomUUID())
                .subscriptionId(subscriptionId)
                .tenantId(tenantId)
                .action(SubscriptionHistoryAction.SUBSCRIPTION_ADDON_REMOVED)
                .performedBy(performedBy)
                .actorType(ActorType.USER)
                .occurredAt(Instant.now())
                .build());

        // Publish add-on deactivated event — after all DB writes.
        addOnEventPublisher.publishDeactivated(deactivated);

        log.info("[SubscriptionAddOnService] remove done subscriptionAddOnId={}", addOn.getId());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static long toMinorUnits(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(CENTS_MULTIPLIER))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact();
    }
}
