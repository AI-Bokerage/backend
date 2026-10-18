package com.company.bsmsvc.api.mapper;

import com.company.bsmsvc.api.dto.response.CreditNoteResponse;
import com.company.bsmsvc.domain.model.CreditNote;
import org.springframework.stereotype.Component;

@Component
public class CreditNoteApiMapper {

    public CreditNoteResponse toResponse(CreditNote creditNote) {
        return new CreditNoteResponse(
            creditNote.getId(),
            creditNote.getTenantId(),
            creditNote.getInvoiceId(),
            creditNote.getCreditNumber(),
            creditNote.getAmountMinor(),
            creditNote.getCurrency(),
            creditNote.getReason(),
            creditNote.getStatus(),
            creditNote.getCreatedBy(),
            creditNote.getCreatedAt(),
            creditNote.getUpdatedAt()
        );
    }
}
