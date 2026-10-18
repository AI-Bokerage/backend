package com.company.bsmsvc.infrastructure.persistence.repository;

import com.company.bsmsvc.domain.enums.RefundStatus;
import com.company.bsmsvc.infrastructure.persistence.entity.RefundRequestEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRequestJpaRepository extends JpaRepository<RefundRequestEntity, UUID> {

    @Query("""
        SELECT r FROM RefundRequestEntity r
        WHERE r.invoiceId = :invoiceId
          AND r.paymentId = :paymentId
          AND r.requestedAmountMinor = :amount
          AND r.status NOT IN (com.company.bsmsvc.domain.enums.RefundStatus.FAILED)
        ORDER BY r.createdAt DESC
        """)
    Optional<RefundRequestEntity> findActiveByInvoiceAndPaymentAndAmount(
        @Param("invoiceId") UUID invoiceId,
        @Param("paymentId") UUID paymentId,
        @Param("amount") long amount);

    List<RefundRequestEntity> findByStatus(RefundStatus status);

    // Only pick up PROVIDER_REFUND_SUCCEEDED records older than a grace period so the
    // main API thread has time to complete local work before recovery races with it.
    List<RefundRequestEntity> findByStatusAndUpdatedAtBefore(RefundStatus status, Instant threshold);

    @Query("""
        SELECT COALESCE(SUM(r.requestedAmountMinor), 0)
        FROM RefundRequestEntity r
        WHERE r.invoiceId = :invoiceId
          AND r.status IN (
              com.company.bsmsvc.domain.enums.RefundStatus.PROVIDER_REFUND_SUCCEEDED,
              com.company.bsmsvc.domain.enums.RefundStatus.RECOVERY_REQUIRED
          )
        """)
    long sumProviderCommittedAmountByInvoiceId(@Param("invoiceId") UUID invoiceId);
}
