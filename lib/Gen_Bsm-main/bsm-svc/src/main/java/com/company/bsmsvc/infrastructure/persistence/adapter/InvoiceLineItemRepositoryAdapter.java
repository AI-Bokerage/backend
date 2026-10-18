package com.company.bsmsvc.infrastructure.persistence.adapter;

import com.company.bsmsvc.domain.model.InvoiceLineItem;
import com.company.bsmsvc.domain.port.InvoiceLineItemRepositoryPort;
import com.company.bsmsvc.infrastructure.persistence.entity.InvoiceLineItemEntity;
import com.company.bsmsvc.infrastructure.persistence.entity.PlatformInvoiceEntity;
import com.company.bsmsvc.infrastructure.persistence.mapper.InvoiceLineItemEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.InvoiceLineItemJpaRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InvoiceLineItemRepositoryAdapter implements InvoiceLineItemRepositoryPort {

    private final InvoiceLineItemJpaRepository invoiceLineItemJpaRepository;
    private final InvoiceLineItemEntityMapper invoiceLineItemEntityMapper;
    private final EntityManager entityManager;

    @Override
    public InvoiceLineItem save(InvoiceLineItem lineItem) {
        InvoiceLineItemEntity entity = invoiceLineItemEntityMapper.toEntity(lineItem);
        entity.setInvoice(entityManager.getReference(PlatformInvoiceEntity.class, lineItem.getInvoiceId()));
        InvoiceLineItemEntity saved = invoiceLineItemJpaRepository.save(entity);
        return invoiceLineItemEntityMapper.toDomain(saved);
    }

    @Override
    public Optional<InvoiceLineItem> findById(UUID id) {
        return invoiceLineItemJpaRepository.findById(id)
            .map(invoiceLineItemEntityMapper::toDomain);
    }

    @Override
    public List<InvoiceLineItem> findByInvoiceId(UUID invoiceId) {
        return invoiceLineItemJpaRepository.findByInvoiceId(invoiceId)
            .stream()
            .map(invoiceLineItemEntityMapper::toDomain)
            .toList();
    }

    @Override
    public void deleteById(UUID id) {
        invoiceLineItemJpaRepository.deleteById(id);
    }
}
