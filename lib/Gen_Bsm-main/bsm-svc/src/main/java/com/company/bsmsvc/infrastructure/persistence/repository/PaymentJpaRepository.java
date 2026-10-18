package com.company.bsmsvc.infrastructure.persistence.repository;

import com.company.bsmsvc.domain.enums.PaymentStatus;
import com.company.bsmsvc.infrastructure.persistence.entity.PaymentEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByExternalPaymentId(String externalPaymentId);
    Optional<PaymentEntity> findByExternalChargeId(String externalChargeId);
    List<PaymentEntity> findByInvoiceId(UUID invoiceId);
    List<PaymentEntity> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant threshold);
}
