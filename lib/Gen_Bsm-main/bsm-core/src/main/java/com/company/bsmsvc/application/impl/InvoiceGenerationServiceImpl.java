package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.application.service.InvoiceGenerationService;
import com.company.bsmsvc.application.service.InvoiceNumberGenerator;
import com.company.bsmsvc.domain.enums.InvoiceSource;
import com.company.bsmsvc.domain.enums.InvoiceStatus;
import com.company.bsmsvc.domain.event.InvoiceCreatedEvent;
import com.company.bsmsvc.domain.event.InvoiceRenewedEvent;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.model.InvoiceLineItem;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import com.company.bsmsvc.domain.port.EventPublisherPort;
import com.company.bsmsvc.domain.model.InvoiceCreatedMessage;
import com.company.bsmsvc.domain.port.InvoiceEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class InvoiceGenerationServiceImpl implements InvoiceGenerationService {

    private static final Set<InvoiceSource> RECURRING_SOURCES =
        EnumSet.of(InvoiceSource.MANUAL, InvoiceSource.SUBSCRIPTION_RENEWAL);

    private final PlatformInvoiceRepositoryPort platformInvoiceRepositoryPort;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final InvoiceEventPublisher invoiceEventPublisher;
    private final EventPublisherPort auditEventPublisher;

    @Override
    public PlatformInvoice generateInvoice(
        UUID tenantId,
        UUID subscriptionId,
        String currency,
        Instant periodStart,
        Instant periodEnd,
        LocalDate dueDate,
        List<InvoiceLineItem> lineItems,
        InvoiceSource source
    ) {
        InvoiceSource resolvedSource = source != null ? source : InvoiceSource.MANUAL;

        // Only recurring sources (MANUAL, SUBSCRIPTION_RENEWAL) participate in per-period uniqueness.
        // UPGRADE, DOWNGRADE, and ADJUSTMENT invoices may coexist with a recurring invoice.
        if (RECURRING_SOURCES.contains(resolvedSource)
            && platformInvoiceRepositoryPort.existsRecurringBySubscriptionIdAndPeriodStartAndPeriodEnd(
                subscriptionId, periodStart, periodEnd)) {
            throw new BusinessRuleViolationException(
                "A recurring invoice already exists for subscription " + subscriptionId
                + " covering period " + periodStart + " to " + periodEnd
                + ". The first successfully created recurring invoice owns that billing period.");
        }

        UUID invoiceId = UUID.randomUUID();
        List<InvoiceLineItem> safeLineItems = lineItems == null ? new ArrayList<>() : new ArrayList<>(lineItems);
        for (int i = 0; i < safeLineItems.size(); i++) {
            InvoiceLineItem item = safeLineItems.get(i);
            if (item == null) continue;
            if (item.getId() == null) item = item.toBuilder().id(UUID.randomUUID()).build();
            if (item.getInvoiceId() == null) item = item.toBuilder().invoiceId(invoiceId).build();
            safeLineItems.set(i, item);
        }

        PlatformInvoice invoice = PlatformInvoice.builder()
            .id(invoiceId)
            .tenantId(tenantId)
            .subscriptionId(subscriptionId)
            .invoiceNumber(invoiceNumberGenerator.generateInvoiceNumber(tenantId, subscriptionId))
            .status(InvoiceStatus.DRAFT)
            .amountDue(0L)
            .amountPaid(0L)
            .currency(currency)
            .periodStart(periodStart)
            .periodEnd(periodEnd)
            .dueDate(dueDate)
            .source(resolvedSource)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .lineItems(new ArrayList<>())
            .pdfGenerationStatus(com.company.bsmsvc.domain.enums.InvoicePdfStatus.PENDING)
            .build();

        safeLineItems.forEach(invoice::addLineItem);
        invoice.openInvoice();
        invoice.registerEvent(new InvoiceCreatedEvent(invoice.getId(), tenantId, subscriptionId, invoice.getInvoiceNumber(), Instant.now()));

        // For renewal invoices, register an additional audit event
        if (resolvedSource == InvoiceSource.SUBSCRIPTION_RENEWAL) {
            invoice.registerEvent(new InvoiceRenewedEvent(invoice.getId(), tenantId, subscriptionId, invoice.getInvoiceNumber(), Instant.now()));
        }

        PlatformInvoice saved;
        try {
            saved = platformInvoiceRepositoryPort.save(invoice);
        } catch (DataIntegrityViolationException ex) {
            if (RECURRING_SOURCES.contains(resolvedSource)) {
                // Two concurrent recurring-invoice requests raced past the soft check; DB partial
                // unique index caught the second one.
                throw new BusinessRuleViolationException(
                    "A recurring invoice already exists for subscription " + subscriptionId
                    + " covering period " + periodStart + " to " + periodEnd
                    + ". The first successfully created recurring invoice owns that billing period.");
            }
            throw ex;
        }

        // NEW audit-only leg: enqueue to the transactional outbox (joins the enclosing
        // @Transactional caller's transaction when one exists — SubscriptionChangeServiceImpl,
        // SubscriptionAddOnServiceImpl, PpmCheckoutServiceImpl all wrap this call in @Transactional
        // today). Known limitation: InvoiceServiceImpl#createInvoice (the plain API path) does not
        // wrap this call in a transaction, so for that path only, this enqueue commits in its own
        // separate transaction immediately after the invoice save rather than atomically with it —
        // same caveat that already applies to the existing publishAfterCommit business publish
        // below (registerSynchronization falls back to an immediate call on that path today).
        // Additive only: does not touch RabbitInvoiceEventPublisher's existing business publish.
        enqueueInvoiceCreatedAudit(saved);

        // Publish InvoiceCreatedMessage after transaction commit (triggers async PDF generation)
        publishAfterCommit(saved);

        return saved;
    }

    private void enqueueInvoiceCreatedAudit(PlatformInvoice saved) {
        Map<String, Object> data = new HashMap<>();
        data.put("invoiceId", saved.getId().toString());
        data.put("tenantId", saved.getTenantId().toString());
        data.put("subscriptionId", saved.getSubscriptionId() != null ? saved.getSubscriptionId().toString() : null);
        data.put("invoiceNumber", saved.getInvoiceNumber());
        data.put("amountDue", saved.getAmountDue());
        data.put("currency", saved.getCurrency());
        data.put("source", saved.getSource() != null ? saved.getSource().name() : null);
        auditEventPublisher.publish("bsm.invoice.created", saved.getTenantId(),
            "Invoice", saved.getId(), null, data);
    }

    private void publishAfterCommit(PlatformInvoice saved) {
        InvoiceCreatedMessage msg = InvoiceCreatedMessage.builder()
            .invoiceId(saved.getId())
            .tenantId(saved.getTenantId())
            .invoiceNumber(saved.getInvoiceNumber())
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
}
