package com.company.bsmsvc.infrastructure.persistence.adapter;

import com.company.bsmsvc.domain.enums.RefundStatus;
import com.company.bsmsvc.domain.model.RefundRequest;
import com.company.bsmsvc.domain.port.RefundRequestRepositoryPort;
import com.company.bsmsvc.infrastructure.persistence.mapper.RefundRequestEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.RefundRequestJpaRepository;
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
public class RefundRequestRepositoryAdapter implements RefundRequestRepositoryPort {

    private final RefundRequestJpaRepository jpaRepository;
    private final RefundRequestEntityMapper mapper;

    @Override @Transactional
    public RefundRequest save(RefundRequest request) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(request)));
    }

    @Override
    public Optional<RefundRequest> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<RefundRequest> findActiveByInvoiceAndPaymentAndAmount(UUID invoiceId, UUID paymentId, long amountMinor) {
        return jpaRepository.findActiveByInvoiceAndPaymentAndAmount(invoiceId, paymentId, amountMinor).map(mapper::toDomain);
    }

    @Override
    public List<RefundRequest> findByStatus(RefundStatus status) {
        return jpaRepository.findByStatus(status).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<RefundRequest> findByStatusOlderThan(RefundStatus status, Instant threshold) {
        return jpaRepository.findByStatusAndUpdatedAtBefore(status, threshold).stream().map(mapper::toDomain).toList();
    }

    @Override
    public long sumProviderCommittedAmountByInvoiceId(UUID invoiceId) {
        return jpaRepository.sumProviderCommittedAmountByInvoiceId(invoiceId);
    }
}
