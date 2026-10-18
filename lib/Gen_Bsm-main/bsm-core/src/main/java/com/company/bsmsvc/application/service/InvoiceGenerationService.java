package com.company.bsmsvc.application.service;

import com.company.bsmsvc.domain.enums.InvoiceSource;
import com.company.bsmsvc.domain.model.InvoiceLineItem;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Low-level invoice creation: builds and persists a new {@link PlatformInvoice} from
 * already-resolved billing data (period, currency, line items).
 *
 * <p>This is the shared creation primitive underneath {@link InvoiceService#createInvoice}
 * (the plain API path, which auto-derives its arguments from the subscription) and is also
 * called directly by other services that already have this data on hand (plan-change,
 * add-on, PPM checkout, and tenant-onboarding flows), typically from within their own
 * {@code @Transactional} boundary. {@link InvoiceService} owns the invoice's later lifecycle
 * (payment application, void, refund); this service only owns creation.
 */
public interface InvoiceGenerationService {

    /**
     * Creates and persists a new {@code DRAFT→OPEN} invoice.
     *
     * @implSpec For "recurring" sources ({@link InvoiceSource#MANUAL} and
     * {@link InvoiceSource#SUBSCRIPTION_RENEWAL}), enforces at most one invoice per
     * subscription per exact {@code [periodStart, periodEnd]} window — a second call for the
     * same subscription/period throws
     * {@link com.company.bsmsvc.domain.exception.BusinessRuleViolationException}; this is
     * additionally enforced by a DB unique constraint to close a race between concurrent
     * callers. {@code UPGRADE}/{@code DOWNGRADE}/{@code ADJUSTMENT} sources are exempt and may
     * coexist with a recurring invoice for the same period. {@code source == null} is treated
     * as {@link InvoiceSource#MANUAL}. Line items with a null id/invoiceId are stamped with
     * generated values.
     * <p>Postconditions: the invoice is persisted {@code OPEN} (moved from {@code DRAFT} via
     * {@code invoice.openInvoice()}), an audit event ({@code bsm.invoice.created}) is enqueued,
     * and — after the transaction commits (or immediately if no transaction is active) — an
     * {@code InvoiceCreatedMessage} is published to trigger async PDF generation.
     */
    PlatformInvoice generateInvoice(
        UUID tenantId,
        UUID subscriptionId,
        String currency,
        Instant periodStart,
        Instant periodEnd,
        LocalDate dueDate,
        List<InvoiceLineItem> lineItems,
        InvoiceSource source
    );
}
