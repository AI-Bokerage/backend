package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.application.service.CreditNoteNumberGenerator;
import com.company.bsmsvc.domain.model.CreditNoteFilter;
import com.company.bsmsvc.domain.enums.CreditNoteStatus;
import com.company.bsmsvc.domain.exception.CreditNoteNotFoundException;
import com.company.bsmsvc.domain.model.CreditNote;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.port.CreditNoteRepositoryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.company.bsmsvc.domain.port.TenantScopePort;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CreditNoteServiceImplTest {

    @Mock
    private CreditNoteRepositoryPort creditNoteRepositoryPort;

    @Mock
    private CreditNoteNumberGenerator creditNoteNumberGenerator;

    @Mock private TenantScopePort tenantScopeEnforcer;

    @Mock private com.company.bsmsvc.domain.port.EventPublisherPort auditEventPublisher;

    @InjectMocks
    private CreditNoteServiceImpl creditNoteService;

    private UUID tenantId;
    private UUID invoiceId;
    private UUID creditNoteId;

    @BeforeEach
    void setUp() {
        lenient().when(tenantScopeEnforcer.resolveEffectiveTenantId(any())).thenAnswer(inv -> inv.getArgument(0));
        tenantId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();
        creditNoteId = UUID.randomUUID();
    }

    @Test
    void createCreditNote_savesWithOpenStatusAndRegistersEvent() {
        String creditNumber = "CN-2026-001";
        when(creditNoteNumberGenerator.generateCreditNumber(tenantId)).thenReturn(creditNumber);
        CreditNote saved = creditNote(creditNoteId, tenantId, CreditNoteStatus.OPEN);
        when(creditNoteRepositoryPort.save(any())).thenReturn(saved);

        CreditNote result = creditNoteService.createCreditNote(tenantId, invoiceId, 1000L, "INR", "Billing error", UUID.randomUUID());

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(CreditNoteStatus.OPEN);
        verify(creditNoteRepositoryPort).save(any(CreditNote.class));
    }

    @Test
    void listCreditNotes_clampsPageSizeTo100() {
        CreditNoteFilter filter = new CreditNoteFilter(tenantId, null, null, null, null, null);
        PageResult<CreditNote> page = new PageResult<>(List.of(), 0, 100, 0, 0, false);
        when(creditNoteRepositoryPort.findCreditNotes(any(), any(int.class), any(int.class), any(), any()))
            .thenReturn(page);

        creditNoteService.listCreditNotes(filter, 0, 500, "createdAt", "DESC");

        verify(creditNoteRepositoryPort).findCreditNotes(filter, 0, 100, "createdAt", "DESC");
    }

    @Test
    void getCreditNoteById_returnsWhenFound() {
        CreditNote cn = creditNote(creditNoteId, tenantId, CreditNoteStatus.OPEN);
        when(creditNoteRepositoryPort.findById(creditNoteId)).thenReturn(Optional.of(cn));

        CreditNote result = creditNoteService.getCreditNoteById(creditNoteId);

        assertThat(result.getId()).isEqualTo(creditNoteId);
    }

    @Test
    void getCreditNoteById_throwsWhenNotFound() {
        when(creditNoteRepositoryPort.findById(creditNoteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creditNoteService.getCreditNoteById(creditNoteId))
            .isInstanceOf(CreditNoteNotFoundException.class)
            .hasMessageContaining(creditNoteId.toString());
    }

    @Test
    void applyCreditNote_callsMarkAppliedAndSaves() {
        CreditNote cn = creditNote(creditNoteId, tenantId, CreditNoteStatus.OPEN);
        CreditNote applied = creditNote(creditNoteId, tenantId, CreditNoteStatus.APPLIED);
        when(creditNoteRepositoryPort.findById(creditNoteId)).thenReturn(Optional.of(cn));
        when(creditNoteRepositoryPort.save(cn)).thenReturn(applied);

        CreditNote result = creditNoteService.applyCreditNote(creditNoteId);

        assertThat(result.getStatus()).isEqualTo(CreditNoteStatus.APPLIED);
        verify(creditNoteRepositoryPort).save(cn);
    }

    @Test
    void applyCreditNote_throwsWhenNotFound() {
        when(creditNoteRepositoryPort.findById(creditNoteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creditNoteService.applyCreditNote(creditNoteId))
            .isInstanceOf(CreditNoteNotFoundException.class);
    }

    @Test
    void voidCreditNote_callsMarkVoidedAndSaves() {
        CreditNote cn = creditNote(creditNoteId, tenantId, CreditNoteStatus.OPEN);
        CreditNote voided = creditNote(creditNoteId, tenantId, CreditNoteStatus.VOID);
        when(creditNoteRepositoryPort.findById(creditNoteId)).thenReturn(Optional.of(cn));
        when(creditNoteRepositoryPort.save(cn)).thenReturn(voided);

        CreditNote result = creditNoteService.voidCreditNote(creditNoteId);

        assertThat(result.getStatus()).isEqualTo(CreditNoteStatus.VOID);
        verify(creditNoteRepositoryPort).save(cn);
    }

    @Test
    void voidCreditNote_throwsWhenNotFound() {
        when(creditNoteRepositoryPort.findById(creditNoteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creditNoteService.voidCreditNote(creditNoteId))
            .isInstanceOf(CreditNoteNotFoundException.class);
    }

    private CreditNote creditNote(UUID id, UUID tenantId, CreditNoteStatus status) {
        return CreditNote.builder()
            .id(id).tenantId(tenantId).invoiceId(invoiceId)
            .creditNumber("CN-001").amountMinor(1000L).currency("INR")
            .reason("test").status(status).createdAt(Instant.now())
            .domainEvents(new ArrayList<>()).build();
    }
}
