package com.company.bsmsvc.application.service;

import com.company.bsmsvc.domain.model.CreditNoteFilter;
import com.company.bsmsvc.domain.model.CreditNote;
import com.company.bsmsvc.domain.model.PageResult;
import java.util.UUID;

/**
 * Creates, lists, retrieves, applies, and voids credit notes against invoices. Not
 * auto-configured by the starter — wire manually with a
 * {@link CreditNoteNumberGenerator} and {@link com.company.bsmsvc.domain.port.CreditNoteRepositoryPort}.
 */
public interface CreditNoteService {
    CreditNote createCreditNote(UUID tenantId, UUID invoiceId, long amountMinor, String currency, String reason, UUID createdBy);
    PageResult<CreditNote> listCreditNotes(CreditNoteFilter filter, int page, int size, String sortBy, String sortDirection);
    CreditNote getCreditNoteById(UUID id);
    CreditNote applyCreditNote(UUID id);
    CreditNote voidCreditNote(UUID id);
}
