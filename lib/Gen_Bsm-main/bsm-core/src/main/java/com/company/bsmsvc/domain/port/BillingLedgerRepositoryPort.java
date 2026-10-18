package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.BillingLedgerEntry;

/**
 * Persistence port for immutable billing ledger entries (payment/refund/adjustment records used
 * for dunning and reconciliation history). Implementations must be thread-safe/stateless.
 */
public interface BillingLedgerRepositoryPort {
    BillingLedgerEntry save(BillingLedgerEntry entry);
}
