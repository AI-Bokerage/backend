package com.company.bsmsvc.application.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.application.service.CreditNoteNumberGenerator;
import com.company.bsmsvc.domain.enums.CreditNoteStatus;
import com.company.bsmsvc.domain.model.CreditNote;
import com.company.bsmsvc.domain.port.CreditNoteRepositoryPort;
import com.company.bsmsvc.domain.port.EventPublisherPort;
import com.company.bsmsvc.domain.port.TenantScopePort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Confirms all 3 requested credit-note lifecycle events (all real, all have a call site):
 * created ({@code createCreditNote} — has a real actor, {@code createdBy}, contradicting the
 * phase brief's blanket "no actor threaded through any of these" — corrected here), applied and
 * voided (both genuinely actor-less today — reported, not fabricated).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CreditNote audit publish — all 3 real call sites")
class CreditNoteAuditPublishTest {

    @Mock private CreditNoteRepositoryPort creditNoteRepositoryPort;
    @Mock private CreditNoteNumberGenerator creditNoteNumberGenerator;
    @Mock private TenantScopePort tenantScopeEnforcer;
    @Mock private EventPublisherPort auditEventPublisher;
    @InjectMocks private CreditNoteServiceImpl creditNoteService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID invoiceId = UUID.randomUUID();
    private final UUID creditNoteId = UUID.randomUUID();
    private final UUID createdBy = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(tenantScopeEnforcer.resolveEffectiveTenantId(any()))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    private CreditNote creditNote(CreditNoteStatus status) {
        return CreditNote.builder()
            .id(creditNoteId).tenantId(tenantId).invoiceId(invoiceId)
            .creditNumber("CN-001").amountMinor(1000L).currency("INR")
            .reason("test").status(status).createdBy(createdBy).createdAt(Instant.now())
            .domainEvents(new ArrayList<>()).build();
    }

    @Test
    @DisplayName("createCreditNote → credit_note.created carries the real createdBy actor")
    void createCreditNote_enqueuesCreatedWithActor() {
        when(creditNoteNumberGenerator.generateCreditNumber(tenantId)).thenReturn("CN-2026-001");
        when(creditNoteRepositoryPort.save(any())).thenReturn(creditNote(CreditNoteStatus.OPEN));

        creditNoteService.createCreditNote(tenantId, invoiceId, 1000L, "INR", "Billing error", createdBy);

        verify(auditEventPublisher).publish(eq("credit_note.created"), eq(tenantId), eq("CreditNote"),
            eq(creditNoteId), eq(createdBy), any());
    }

    @Test
    @DisplayName("applyCreditNote → credit_note.applied with null actor (none threaded through this path)")
    void applyCreditNote_enqueuesAppliedWithNullActor() {
        CreditNote open = creditNote(CreditNoteStatus.OPEN);
        when(creditNoteRepositoryPort.findById(creditNoteId)).thenReturn(Optional.of(open));
        when(creditNoteRepositoryPort.save(open)).thenReturn(creditNote(CreditNoteStatus.APPLIED));

        creditNoteService.applyCreditNote(creditNoteId);

        verify(auditEventPublisher).publish(eq("credit_note.applied"), eq(tenantId), eq("CreditNote"),
            eq(creditNoteId), org.mockito.Mockito.isNull(), any());
    }

    @Test
    @DisplayName("voidCreditNote → credit_note.voided with null actor (none threaded through this path)")
    void voidCreditNote_enqueuesVoidedWithNullActor() {
        CreditNote open = creditNote(CreditNoteStatus.OPEN);
        when(creditNoteRepositoryPort.findById(creditNoteId)).thenReturn(Optional.of(open));
        when(creditNoteRepositoryPort.save(open)).thenReturn(creditNote(CreditNoteStatus.VOID));

        creditNoteService.voidCreditNote(creditNoteId);

        verify(auditEventPublisher).publish(eq("credit_note.voided"), eq(tenantId), eq("CreditNote"),
            eq(creditNoteId), org.mockito.Mockito.isNull(), any());
    }
}
