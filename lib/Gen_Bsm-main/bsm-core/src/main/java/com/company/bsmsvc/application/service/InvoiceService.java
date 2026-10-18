package com.company.bsmsvc.application.service;

import com.company.bsmsvc.domain.model.InvoiceFilter;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import java.util.UUID;

/**
 * Invoice lifecycle operations (creation from the plain API path, lookup, and status
 * transitions such as payment application, voiding, and refunding).
 *
 * <p>{@link #createInvoice} is a thin convenience wrapper over
 * {@link InvoiceGenerationService#generateInvoice} that auto-derives billing period, currency
 * and line items from the subscription when the caller does not supply them; callers that
 * already have this data (e.g. plan-change flows) may call {@link InvoiceGenerationService}
 * directly instead. See {@link InvoiceGenerationService} for the invoice-creation invariants.
 */
public interface InvoiceService {

    /**
     * Creates an invoice for a tenant/subscription via the plain API path.
     *
     * @implSpec Requires {@code invoice.tenantId} and, when {@code periodStart}/line items are
     * not supplied, a resolvable {@code invoice.subscriptionId} — the subscription and the
     * tenant's {@code TenantBillingProfile} (for currency) must already exist, else
     * {@link com.company.bsmsvc.domain.exception.BusinessRuleViolationException} is thrown.
     * When line items are auto-populated, the subscription must have a resolved PPM price
     * ({@code ppmResolvedPriceMinor}), else throws the same exception. Delegates the actual
     * creation (uniqueness check, numbering, DRAFT→OPEN transition, event publishing) to
     * {@link InvoiceGenerationService#generateInvoice}.
     */
    PlatformInvoice createInvoice(PlatformInvoice invoice);

    /** Tenant-scoped lookup by id; throws {@code InvoiceNotFoundException} if absent. */
    PlatformInvoice getInvoiceById(UUID invoiceId);

    /** Lookup by human-readable invoice number; not tenant-scope-enforced. */
    PlatformInvoice getInvoiceByNumber(String invoiceNumber);

    /** Paged, tenant-scoped invoice search. */
    PageResult<PlatformInvoice> searchInvoices(InvoiceFilter filter, int page, int size, String sortBy, String sortDirection);

    /**
     * Records a payment against an invoice, transitioning it towards {@code PAID} or
     * {@code PARTIALLY_PAID}.
     *
     * @implSpec Preconditions: the invoice (looked up tenant-scoped via {@link #getInvoiceById})
     * must exist; {@code amountPaid}, if non-null, must be {@code > 0} or this throws
     * {@link com.company.bsmsvc.domain.exception.BusinessRuleViolationException}. This method
     * does not itself check the invoice's current status (e.g. it does not require
     * {@code OPEN}) — that is left to the domain entity's own transition rules.
     * <p>Status transition: {@code amountPaid == null} or {@code amountPaid >= amountDue} marks
     * the invoice fully {@code PAID}; a smaller positive amount marks it {@code PARTIALLY_PAID}.
     * Postconditions: the invoice's cached PDF is invalidated and regeneration is triggered
     * asynchronously after the transaction commits, and a {@code bsm.invoice.paid} audit event
     * is published.
     */
    PlatformInvoice applyPayment(UUID invoiceId, Long amountPaid, UUID actorId);

    /**
     * Voids an invoice.
     *
     * @implSpec The allowed source statuses for voiding are enforced by
     * {@code PlatformInvoice.voidInvoice()} itself, not by this service method. Publishes a
     * {@code bsm.invoice.voided} audit event on success.
     */
    PlatformInvoice voidInvoice(UUID invoiceId, UUID actorId);

    /**
     * Marks a (typically already-paid) invoice as refunded.
     *
     * @implSpec Idempotent: if the invoice is already {@code REFUNDED}, returns it unchanged.
     * Otherwise invalidates the cached PDF, triggers async regeneration, and publishes a
     * {@code bsm.invoice.refunded} audit event.
     */
    PlatformInvoice markRefunded(UUID invoiceId, UUID actorId);
}
