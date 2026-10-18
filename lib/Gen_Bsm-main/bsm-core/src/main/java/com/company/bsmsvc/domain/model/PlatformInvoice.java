package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.InvoiceStatus;
import com.company.bsmsvc.domain.event.InvoiceCreatedEvent;
import com.company.bsmsvc.domain.event.InvoiceMarkedPaidEvent;
import com.company.bsmsvc.domain.event.InvoiceVoidedEvent;
import com.company.bsmsvc.domain.event.LineItemAddedEvent;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PlatformInvoice {

    private UUID id;
    private UUID tenantId;
    private UUID subscriptionId;
    private String invoiceNumber;
    private InvoiceStatus status;
    private com.company.bsmsvc.domain.enums.InvoiceSource source;
    private long amountDue;
    private long amountPaid;
    private String currency;
    private Instant periodStart;
    private Instant periodEnd;
    private LocalDate dueDate;
    private Instant paidAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
    private String pdfUrl;
    private Instant pdfGeneratedAt;
    private com.company.bsmsvc.domain.enums.InvoicePdfStatus pdfGenerationStatus;

    @Builder.Default
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    @Builder.Default
    private List<Object> domainEvents = new ArrayList<>();

    // ---- Domain Events Helpers ----

    public void registerEvent(Object event) {
        if (this.domainEvents == null) {
            this.domainEvents = new ArrayList<>();
        }
        this.domainEvents.add(event);
    }

    public List<Object> pullDomainEvents() {
        if (this.domainEvents == null || this.domainEvents.isEmpty()) {
            return Collections.emptyList();
        }
        List<Object> snapshot = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return snapshot;
    }

    public void clearDomainEvents() {
        if (this.domainEvents != null) {
            this.domainEvents.clear();
        }
    }

    // ---- PDF Generation Helpers ----

    public void markPdfProcessing() {
        this.pdfGenerationStatus = com.company.bsmsvc.domain.enums.InvoicePdfStatus.PROCESSING;
    }

    public void markPdfGenerated(String pdfUrl) {
        this.pdfGenerationStatus = com.company.bsmsvc.domain.enums.InvoicePdfStatus.GENERATED;
        this.pdfUrl = pdfUrl;
        this.pdfGeneratedAt = Instant.now();
        registerEvent(new com.company.bsmsvc.domain.event.InvoicePdfGeneratedEvent(this.id, this.tenantId, pdfUrl, this.pdfGeneratedAt));
    }

    public void markPdfFailed() {
        this.pdfGenerationStatus = com.company.bsmsvc.domain.enums.InvoicePdfStatus.FAILED;
        registerEvent(new com.company.bsmsvc.domain.event.InvoicePdfGenerationFailedEvent(this.id, this.tenantId, Instant.now()));
    }

    public void resetPdfForRegeneration() {
        this.pdfGenerationStatus = com.company.bsmsvc.domain.enums.InvoicePdfStatus.PENDING;
        this.pdfUrl = null;
        this.pdfGeneratedAt = null;
    }

    // ---- Aggregate Operations ----

    /**
     * Adds a line item to the invoice and updates the totals.
     * Prevents orphan line items by binding the item to this invoice.
     */
    public void addLineItem(InvoiceLineItem item) {
        if (item == null) {
            throw new BusinessRuleViolationException("Line item cannot be null");
        }

        InvoiceLineItem itemToUpdate = item;
        if (item.getInvoiceId() == null) {
            itemToUpdate = item.toBuilder().invoiceId(this.id).build();
        } else if (!item.getInvoiceId().equals(this.id)) {
            throw new BusinessRuleViolationException("Line item belongs to a different invoice");
        }

        itemToUpdate.validate();

        if (this.lineItems == null) {
            this.lineItems = new ArrayList<>();
        }

        this.lineItems.add(itemToUpdate);
        calculateTotal();

        registerEvent(new LineItemAddedEvent(this.id, itemToUpdate.getId(), Instant.now()));
    }

    /**
     * Removes a line item by ID and recalculates totals.
     */
    public void removeLineItem(UUID lineItemId) {
        if (lineItemId == null) {
            return;
        }
        if (this.lineItems == null) {
            return;
        }
        boolean removed = this.lineItems.removeIf(item -> lineItemId.equals(item.getId()));
        if (removed) {
            calculateTotal();
        }
    }

    /**
     * Calculates the sum of all line item amounts as amountDue.
     */
    public void calculateTotal() {
        if (this.lineItems == null || this.lineItems.isEmpty()) {
            this.amountDue = 0L;
            return;
        }
        this.amountDue = this.lineItems.stream()
            .mapToLong(InvoiceLineItem::getAmountMinor)
            .sum();
    }

    // ---- State Machine Operations ----

    /**
     * Publishes a draft invoice, moving it to OPEN status.
     * Allowed: DRAFT -> OPEN
     */
    public void openInvoice() {
        if (this.status == InvoiceStatus.OPEN) {
            return;
        }
        if (this.status != InvoiceStatus.DRAFT) {
            throw new BusinessRuleViolationException("Transition to OPEN is not allowed from status: " + this.status);
        }
        this.status = InvoiceStatus.OPEN;
    }

    /**
     * Marks the invoice as PAID.
     * Allowed: OPEN -> PAID, PARTIALLY_PAID -> PAID
     */
    public void markPaid(Instant paidAt) {
        if (this.status == InvoiceStatus.PAID) {
            return;
        }
        if (this.status != InvoiceStatus.OPEN && this.status != InvoiceStatus.PARTIALLY_PAID) {
            throw new BusinessRuleViolationException("Transition to PAID is not allowed from status: " + this.status);
        }
        this.status = InvoiceStatus.PAID;
        this.amountPaid = this.amountDue;
        this.paidAt = paidAt;
        registerEvent(new InvoiceMarkedPaidEvent(this.id, this.tenantId, paidAt));
    }

    /**
     * Marks the invoice as PARTIALLY_PAID.
     * Allowed: OPEN -> PARTIALLY_PAID, PARTIALLY_PAID -> PARTIALLY_PAID
     */
    public void markPartiallyPaid(long amountPaid, Instant occurredAt) {
        if (this.status == InvoiceStatus.PAID) {
            return;
        }
        if (this.status != InvoiceStatus.OPEN && this.status != InvoiceStatus.PARTIALLY_PAID) {
            throw new BusinessRuleViolationException("Transition to PARTIALLY_PAID is not allowed from status: " + this.status);
        }
        if (amountPaid < 0) {
            throw new BusinessRuleViolationException("Amount paid cannot be negative: " + amountPaid);
        }
        this.amountPaid = amountPaid;
        if (this.amountPaid >= this.amountDue) {
            this.status = InvoiceStatus.PAID;
            this.paidAt = occurredAt;
            registerEvent(new InvoiceMarkedPaidEvent(this.id, this.tenantId, occurredAt));
        } else {
            this.status = InvoiceStatus.PARTIALLY_PAID;
        }
    }

    /**
     * Voids the invoice.
     * Allowed: OPEN -> VOID, PARTIALLY_PAID -> VOID
     */
    public void voidInvoice() {
        if (this.status == InvoiceStatus.VOID) {
            return;
        }
        if (this.status != InvoiceStatus.OPEN && this.status != InvoiceStatus.PARTIALLY_PAID) {
            throw new BusinessRuleViolationException("Transition to VOID is not allowed from status: " + this.status);
        }
        this.status = InvoiceStatus.VOID;
        registerEvent(new InvoiceVoidedEvent(this.id, this.tenantId, Instant.now()));
    }

    /**
     * Refunds the invoice.
     * Allowed: PAID -> REFUNDED
     */
    public void refund() {
        if (this.status == InvoiceStatus.REFUNDED) {
            return;
        }
        if (this.status != InvoiceStatus.PAID) {
            throw new BusinessRuleViolationException("Transition to REFUNDED is not allowed from status: " + this.status);
        }
        this.status = InvoiceStatus.REFUNDED;
    }

    // ---- Status Helpers ----

    public boolean isPaid() {
        return this.status == InvoiceStatus.PAID;
    }

    public boolean isOpen() {
        return this.status == InvoiceStatus.OPEN;
    }
}
