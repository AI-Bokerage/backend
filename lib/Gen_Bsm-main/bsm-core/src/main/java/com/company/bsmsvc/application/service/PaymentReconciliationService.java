package com.company.bsmsvc.application.service;

/**
 * Reconciles stale {@code PENDING} payments against the payment gateway. Not auto-configured by
 * the starter; intended to be invoked periodically by the host (e.g. a {@code @Scheduled}
 * method) — see {@code INTEGRATION_GUIDE.md}'s Scheduler section.
 */
public interface PaymentReconciliationService {
    void reconcilePendingPayments();
}
