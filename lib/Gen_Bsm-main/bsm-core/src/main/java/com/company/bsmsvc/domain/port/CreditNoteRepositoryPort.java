package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.CreditNoteFilter;
import com.company.bsmsvc.domain.model.CreditNote;
import com.company.bsmsvc.domain.model.PageResult;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence/query port for credit notes issued against invoices (refunds, adjustments).
 * Implementations must be thread-safe/stateless.
 */
public interface CreditNoteRepositoryPort {
    CreditNote save(CreditNote creditNote);
    Optional<CreditNote> findById(UUID id);
    PageResult<CreditNote> findCreditNotes(CreditNoteFilter filter, int page, int size, String sortBy, String sortDirection);
}
