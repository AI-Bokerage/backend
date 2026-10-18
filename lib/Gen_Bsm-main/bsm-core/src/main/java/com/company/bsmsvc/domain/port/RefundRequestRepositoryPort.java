package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.enums.RefundStatus;
import com.company.bsmsvc.domain.model.RefundRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRequestRepositoryPort {
    RefundRequest save(RefundRequest request);
    Optional<RefundRequest> findById(UUID id);

    /**
     * Idempotency lookup: find an active (non-FAILED) request for the same invoice + payment + amount.
     * Used to prevent duplicate provider calls.
     */
    Optional<RefundRequest> findActiveByInvoiceAndPaymentAndAmount(UUID invoiceId, UUID paymentId, long amountMinor);

    /**
     * Returns all requests in the given status (used by recovery scheduler).
     */
    List<RefundRequest> findByStatus(RefundStatus status);
    List<RefundRequest> findByStatusOlderThan(RefundStatus status, Instant threshold);

    /**
     * Sum of requestedAmountMinor for requests that are committed at the provider
     * but whose credit note may not exist yet.
     * Used to close the over-refund gap during concurrent requests.
     */
    long sumProviderCommittedAmountByInvoiceId(UUID invoiceId);
}
