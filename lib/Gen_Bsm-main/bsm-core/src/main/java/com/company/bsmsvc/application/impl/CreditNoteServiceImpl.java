package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.application.service.CreditNoteNumberGenerator;
import com.company.bsmsvc.application.service.CreditNoteService;
import com.company.bsmsvc.domain.model.CreditNoteFilter;
import com.company.bsmsvc.domain.enums.CreditNoteStatus;
import com.company.bsmsvc.domain.event.CreditNoteCreatedEvent;
import com.company.bsmsvc.domain.exception.CreditNoteNotFoundException;
import com.company.bsmsvc.domain.model.CreditNote;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.port.CreditNoteRepositoryPort;
import com.company.bsmsvc.domain.port.EventPublisherPort;
import com.company.bsmsvc.domain.port.TenantScopePort;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditNoteServiceImpl implements CreditNoteService {

    private final CreditNoteRepositoryPort creditNoteRepositoryPort;
    private final CreditNoteNumberGenerator creditNoteNumberGenerator;
    private final TenantScopePort tenantScopeEnforcer;
    private final EventPublisherPort auditEventPublisher;

    public CreditNoteServiceImpl(CreditNoteRepositoryPort creditNoteRepositoryPort,
                                 CreditNoteNumberGenerator creditNoteNumberGenerator,
                                 TenantScopePort tenantScopeEnforcer,
                                 EventPublisherPort auditEventPublisher) {
        this.creditNoteRepositoryPort = creditNoteRepositoryPort;
        this.creditNoteNumberGenerator = creditNoteNumberGenerator;
        this.tenantScopeEnforcer = tenantScopeEnforcer;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Override
    @Transactional
    public CreditNote createCreditNote(UUID tenantId, UUID invoiceId, long amountMinor,
                                       String currency, String reason, UUID createdBy) {
        tenantScopeEnforcer.assertTenantAccess(tenantId);
        String creditNumber = creditNoteNumberGenerator.generateCreditNumber(tenantId);
        CreditNote cn = CreditNote.builder()
            .id(UUID.randomUUID())
            .tenantId(tenantId)
            .invoiceId(invoiceId)
            .creditNumber(creditNumber)
            .amountMinor(amountMinor)
            .currency(currency)
            .reason(reason)
            .status(CreditNoteStatus.OPEN)
            .createdBy(createdBy)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        cn.registerEvent(new CreditNoteCreatedEvent(
            cn.getId(), cn.getTenantId(), cn.getInvoiceId(), cn.getCreditNumber(), cn.getAmountMinor(), Instant.now()
        ));
        CreditNote saved = creditNoteRepositoryPort.save(cn);
        Map<String, Object> data = new HashMap<>();
        data.put("creditNoteId", saved.getId().toString());
        data.put("invoiceId", saved.getInvoiceId().toString());
        data.put("creditNumber", saved.getCreditNumber());
        data.put("amountMinor", saved.getAmountMinor());
        data.put("currency", saved.getCurrency());
        data.put("reason", saved.getReason());
        auditEventPublisher.publish("credit_note.created", saved.getTenantId(), "CreditNote",
            saved.getId(), createdBy, data);
        return saved;
    }

    @Override
    public PageResult<CreditNote> listCreditNotes(CreditNoteFilter filter, int page, int size,
                                                   String sortBy, String sortDirection) {
        CreditNoteFilter effectiveFilter = new CreditNoteFilter(
            tenantScopeEnforcer.resolveEffectiveTenantId(filter.tenantId()),
            filter.invoiceId(), filter.status(), filter.creditNumber(),
            filter.createdFrom(), filter.createdTo()
        );
        int clampedSize = Math.min(size, 100);
        return creditNoteRepositoryPort.findCreditNotes(effectiveFilter, page, clampedSize, sortBy, sortDirection);
    }

    @Override
    public CreditNote getCreditNoteById(UUID id) {
        CreditNote cn = creditNoteRepositoryPort.findById(id)
            .orElseThrow(() -> new CreditNoteNotFoundException("CreditNote not found: " + id));
        tenantScopeEnforcer.assertTenantAccess(cn.getTenantId());
        return cn;
    }

    @Override
    @Transactional
    public CreditNote applyCreditNote(UUID id) {
        CreditNote cn = creditNoteRepositoryPort.findById(id)
            .orElseThrow(() -> new CreditNoteNotFoundException("CreditNote not found: " + id));
        tenantScopeEnforcer.assertTenantAccess(cn.getTenantId());
        cn.markApplied();
        CreditNote saved = creditNoteRepositoryPort.save(cn);
        // No actor threaded through this call path today — reported, not fabricated.
        auditEventPublisher.publish("credit_note.applied", saved.getTenantId(), "CreditNote",
            saved.getId(), null, creditNoteAuditData(saved));
        return saved;
    }

    @Override
    @Transactional
    public CreditNote voidCreditNote(UUID id) {
        CreditNote cn = creditNoteRepositoryPort.findById(id)
            .orElseThrow(() -> new CreditNoteNotFoundException("CreditNote not found: " + id));
        tenantScopeEnforcer.assertTenantAccess(cn.getTenantId());
        cn.markVoided();
        CreditNote saved = creditNoteRepositoryPort.save(cn);
        // No actor threaded through this call path today — reported, not fabricated.
        auditEventPublisher.publish("credit_note.voided", saved.getTenantId(), "CreditNote",
            saved.getId(), null, creditNoteAuditData(saved));
        return saved;
    }

    private Map<String, Object> creditNoteAuditData(CreditNote cn) {
        Map<String, Object> data = new HashMap<>();
        data.put("creditNoteId", cn.getId().toString());
        data.put("invoiceId", cn.getInvoiceId().toString());
        data.put("creditNumber", cn.getCreditNumber());
        data.put("amountMinor", cn.getAmountMinor());
        data.put("currency", cn.getCurrency());
        data.put("status", cn.getStatus() != null ? cn.getStatus().name() : null);
        return data;
    }
}
