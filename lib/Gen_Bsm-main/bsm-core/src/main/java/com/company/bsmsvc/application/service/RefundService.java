package com.company.bsmsvc.application.service;

import com.company.bsmsvc.domain.model.CreditNote;
import java.util.UUID;

/**
 * Creates refunds (recorded as credit notes) against invoices. Not auto-configured by the
 * starter — wire manually alongside {@link CreditNoteService}.
 */
public interface RefundService {
    CreditNote createRefund(UUID tenantId, UUID invoiceId, long amountMinor, String reason, UUID requestedBy);
}
