package com.company.bsmsvc.application.service;

import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.Subscription;

public interface InvoiceRenewalService {
    /** Generate renewal invoices for all ACTIVE subscriptions that have reached their period end. */
    void generateDueRenewalInvoices();

    /** Generate a renewal invoice for a single subscription. Idempotent — skips if already exists for the period. */
    PlatformInvoice generateRenewalInvoice(Subscription subscription);
}
