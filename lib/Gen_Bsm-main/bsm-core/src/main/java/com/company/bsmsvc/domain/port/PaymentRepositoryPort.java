package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.Payment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence/query port for payment records, including lookup by external gateway identifiers
 * and stale-pending-payment queries used by reconciliation. Implementations must be
 * thread-safe/stateless.
 */
public interface PaymentRepositoryPort {
    Payment save(Payment payment);
    Optional<Payment> findById(UUID id);
    Optional<Payment> findByExternalPaymentId(String externalPaymentId);
    Optional<Payment> findByExternalChargeId(String externalChargeId);
    List<Payment> findByInvoiceId(UUID invoiceId);
    List<Payment> findPendingOlderThan(Instant threshold);
}
