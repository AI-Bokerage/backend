package com.company.bsmsvc.infrastructure.persistence.adapter;

import com.company.bsmsvc.domain.enums.PaymentStatus;
import com.company.bsmsvc.domain.model.Payment;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import com.company.bsmsvc.infrastructure.persistence.mapper.PaymentEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.PaymentJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentRepositoryAdapter implements PaymentRepositoryPort {

    private final PaymentJpaRepository jpaRepository;
    private final PaymentEntityMapper mapper;

    @Override @Transactional
    public Payment save(Payment payment) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(payment)));
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findByExternalPaymentId(String externalPaymentId) {
        return jpaRepository.findByExternalPaymentId(externalPaymentId).map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findByExternalChargeId(String externalChargeId) {
        return jpaRepository.findByExternalChargeId(externalChargeId).map(mapper::toDomain);
    }

    @Override
    public List<Payment> findByInvoiceId(UUID invoiceId) {
        return jpaRepository.findByInvoiceId(invoiceId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Payment> findPendingOlderThan(Instant threshold) {
        return jpaRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, threshold)
            .stream().map(mapper::toDomain).toList();
    }
}
