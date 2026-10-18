package com.company.bsmsvc.infrastructure.persistence.repository;

import com.company.bsmsvc.infrastructure.persistence.entity.InvoiceLineItemEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceLineItemJpaRepository extends JpaRepository<InvoiceLineItemEntity, UUID> {

    List<InvoiceLineItemEntity> findByInvoiceId(UUID invoiceId);
}
