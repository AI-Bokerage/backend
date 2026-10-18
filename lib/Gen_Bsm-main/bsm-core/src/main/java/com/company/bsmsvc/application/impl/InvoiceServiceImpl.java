package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.application.service.InvoiceGenerationService;
import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.model.InvoiceCreatedMessage;
import com.company.bsmsvc.domain.port.InvoiceEventPublisher;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.InvoiceLineItemType;
import com.company.bsmsvc.domain.enums.InvoiceSource;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.exception.InvoiceNotFoundException;
import com.company.bsmsvc.domain.model.InvoiceFilter;
import com.company.bsmsvc.domain.model.InvoiceLineItem;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import com.company.bsmsvc.domain.model.SubscriptionAddOn;
import com.company.bsmsvc.domain.port.SubscriptionAddOnRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import java.util.ArrayList;
import com.company.bsmsvc.domain.port.EventPublisherPort;
import com.company.bsmsvc.domain.port.TenantScopePort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private final PlatformInvoiceRepositoryPort platformInvoiceRepositoryPort;
    private final InvoiceGenerationService invoiceGenerationService;
    private final SubscriptionRepositoryPort subscriptionRepository;
    private final SubscriptionAddOnRepositoryPort subscriptionAddOnRepository;
    private final TenantBillingProfileService billingProfileService;
    private final InvoiceEventPublisher invoiceEventPublisher;
    private final TenantScopePort tenantScopeEnforcer;
    private final EventPublisherPort auditEventPublisher;

    @Override
    public PlatformInvoice createInvoice(PlatformInvoice invoice) {
        if (invoice == null) {
            throw new BusinessRuleViolationException("Invoice payload cannot be null");
        }
        tenantScopeEnforcer.assertTenantAccess(invoice.getTenantId());

        // Derive billing period from the subscription's current active period when not provided.
        // This is the API path — the caller supplied only tenantId + subscriptionId.
        if (invoice.getPeriodStart() == null) {
            UUID subId = invoice.getSubscriptionId();
            Subscription sub = subscriptionRepository.findById(subId)
                .orElseThrow(() -> new BusinessRuleViolationException(
                    "Subscription not found: " + subId));
            Instant periodStart = sub.getCurrentPeriodStart();
            Instant periodEnd   = sub.getCurrentPeriodEnd();
            LocalDate dueDate   = periodStart.plus(7, ChronoUnit.DAYS).atZone(ZoneOffset.UTC).toLocalDate();
            String currency = billingProfileService.getProfile(sub.getTenantId()).getCurrency();
            invoice = invoice.toBuilder()
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .dueDate(dueDate)
                .currency(currency)
                .build();
        }

        List<InvoiceLineItem> lineItems = invoice.getLineItems();

        // Auto-populate line items from subscription plan when caller does not provide them
        if ((lineItems == null || lineItems.isEmpty()) && invoice.getSubscriptionId() != null) {
            lineItems = buildLineItemsFromSubscription(invoice.getSubscriptionId());
        }

        InvoiceSource source = invoice.getSource() != null ? invoice.getSource() : InvoiceSource.MANUAL;

        return invoiceGenerationService.generateInvoice(
            invoice.getTenantId(),
            invoice.getSubscriptionId(),
            invoice.getCurrency(),
            invoice.getPeriodStart(),
            invoice.getPeriodEnd(),
            invoice.getDueDate(),
            lineItems,
            source
        );
    }

    @Override
    public PlatformInvoice getInvoiceById(UUID invoiceId) {
        PlatformInvoice invoice = platformInvoiceRepositoryPort.findById(invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoiceId));
        tenantScopeEnforcer.assertTenantAccess(invoice.getTenantId());
        return invoice;
    }

    @Override
    public PlatformInvoice getInvoiceByNumber(String invoiceNumber) {
        return platformInvoiceRepositoryPort.findByInvoiceNumber(invoiceNumber)
            .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoiceNumber));
    }

    @Override
    public PageResult<PlatformInvoice> searchInvoices(InvoiceFilter filter, int page, int size, String sortBy, String sortDirection) {
        InvoiceFilter effectiveFilter = new InvoiceFilter(
            tenantScopeEnforcer.resolveEffectiveTenantId(filter.tenantId()),
            filter.subscriptionId(), filter.invoiceNumber(), filter.status()
        );
        return platformInvoiceRepositoryPort.findInvoices(effectiveFilter, page, size, sortBy, sortDirection);
    }

    @Override
    @Transactional
    public PlatformInvoice applyPayment(UUID invoiceId, Long amountPaid, UUID actorId) {
        PlatformInvoice invoice = getInvoiceById(invoiceId);
        Instant now = Instant.now();
        if (amountPaid == null) {
            invoice.markPaid(now);
        } else if (amountPaid <= 0) {
            throw new BusinessRuleViolationException("Payment amount must be greater than zero");
        } else if (amountPaid >= invoice.getAmountDue()) {
            invoice.markPaid(now);
        } else {
            invoice.markPartiallyPaid(amountPaid, now);
        }
        invoice.resetPdfForRegeneration();
        PlatformInvoice saved = platformInvoiceRepositoryPort.save(invoice);
        triggerPdfRegeneration(saved);

        Map<String, Object> data = new HashMap<>();
        data.put("invoiceId", saved.getId().toString());
        data.put("invoiceNumber", saved.getInvoiceNumber());
        data.put("amountPaid", amountPaid);
        data.put("status", saved.getStatus().name());
        auditEventPublisher.publish("bsm.invoice.paid", saved.getTenantId(), "PlatformInvoice",
            saved.getId(), actorId, data);

        return saved;
    }

    private void triggerPdfRegeneration(PlatformInvoice invoice) {
        InvoiceCreatedMessage msg = InvoiceCreatedMessage.builder()
            .invoiceId(invoice.getId())
            .tenantId(invoice.getTenantId())
            .invoiceNumber(invoice.getInvoiceNumber())
            .eventId(UUID.randomUUID())
            .occurredAt(Instant.now())
            .build();
        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invoiceEventPublisher.publishInvoiceCreated(msg);
                }
            });
        } catch (Exception ex) {
            invoiceEventPublisher.publishInvoiceCreated(msg);
        }
    }

    @Override
    public PlatformInvoice voidInvoice(UUID invoiceId, UUID actorId) {
        PlatformInvoice invoice = getInvoiceById(invoiceId);
        invoice.voidInvoice();
        PlatformInvoice saved = platformInvoiceRepositoryPort.save(invoice);

        auditEventPublisher.publish("bsm.invoice.voided", saved.getTenantId(), "PlatformInvoice",
            saved.getId(), actorId,
            Map.of("invoiceId", saved.getId().toString(), "invoiceNumber", saved.getInvoiceNumber()));

        return saved;
    }

    @Override
    public PlatformInvoice markRefunded(UUID invoiceId, UUID actorId) {
        PlatformInvoice invoice = getInvoiceById(invoiceId);
        if (invoice.getStatus() == com.company.bsmsvc.domain.enums.InvoiceStatus.REFUNDED) {
            return invoice;
        }
        invoice.refund();
        invoice.resetPdfForRegeneration();
        PlatformInvoice saved = platformInvoiceRepositoryPort.save(invoice);
        triggerPdfRegeneration(saved);

        auditEventPublisher.publish("bsm.invoice.refunded", saved.getTenantId(), "PlatformInvoice",
            saved.getId(), actorId,
            Map.of("invoiceId", saved.getId().toString(), "invoiceNumber", saved.getInvoiceNumber()));

        return saved;
    }

    // ── Auto-population ───────────────────────────────────────────────────────

    private List<InvoiceLineItem> buildLineItemsFromSubscription(UUID subscriptionId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new BusinessRuleViolationException("Subscription not found for auto-population: " + subscriptionId));

        final long unitAmount;
        final String description;

        if (sub.getPpmResolvedPriceMinor() != null) {
            unitAmount = sub.getPpmResolvedPriceMinor();
            description = "Subscription — " + sub.getBillingCycle().name().toLowerCase();
        } else {
            throw new BusinessRuleViolationException(
                "Cannot auto-populate invoice line items: subscription has no PPM resolved price. subscriptionId=" + subscriptionId);
        }

        List<InvoiceLineItem> items = new ArrayList<>();
        items.add(InvoiceLineItem.builder()
            .id(UUID.randomUUID())
            .itemType(InvoiceLineItemType.SUBSCRIPTION)
            .description(description)
            .quantity(1)
            .unitAmountMinor(unitAmount)
            .amountMinor(unitAmount)
            .createdAt(Instant.now())
            .build());

        // C4: append locked add-on prices — no PPM call needed (grandfathering invariant)
        for (SubscriptionAddOn addOn : subscriptionAddOnRepository.findActiveBySubscriptionId(subscriptionId)) {
            items.add(InvoiceLineItem.builder()
                .id(UUID.randomUUID())
                .itemType(InvoiceLineItemType.ADDON)
                .description("PPM add-on: " + addOn.getPpmAddOnId())
                .quantity(1)
                .unitAmountMinor(addOn.getPpmResolvedPriceMinor())
                .amountMinor(addOn.getPpmResolvedPriceMinor())
                .createdAt(Instant.now())
                .build());
        }

        return List.copyOf(items);
    }
}
