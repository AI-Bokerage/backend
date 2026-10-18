package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.InvoiceLineItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence/query port for individual invoice line items. Implementations must be
 * thread-safe/stateless.
 */
public interface InvoiceLineItemRepositoryPort {

    InvoiceLineItem save(InvoiceLineItem lineItem);

    Optional<InvoiceLineItem> findById(UUID id);

    List<InvoiceLineItem> findByInvoiceId(UUID invoiceId);

    void deleteById(UUID id);
}
