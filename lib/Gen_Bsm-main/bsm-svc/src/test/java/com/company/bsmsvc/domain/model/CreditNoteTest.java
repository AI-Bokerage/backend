package com.company.bsmsvc.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.bsmsvc.domain.enums.CreditNoteStatus;
import com.company.bsmsvc.domain.event.CreditNoteAppliedEvent;
import com.company.bsmsvc.domain.event.CreditNoteVoidedEvent;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreditNoteTest {

    private CreditNote creditNote;

    @BeforeEach
    void setUp() {
        creditNote = CreditNote.builder()
            .id(UUID.randomUUID())
            .tenantId(UUID.randomUUID())
            .invoiceId(UUID.randomUUID())
            .creditNumber("CN-2026-001")
            .amountMinor(5000L)
            .currency("INR")
            .reason("Billing error")
            .status(CreditNoteStatus.OPEN)
            .createdBy(UUID.randomUUID())
            .createdAt(Instant.now())
            .domainEvents(new ArrayList<>())
            .build();
    }

    @Test
    void markApplied_transitionsFromOpen() {
        creditNote.markApplied();
        assertThat(creditNote.getStatus()).isEqualTo(CreditNoteStatus.APPLIED);
    }

    @Test
    void markApplied_registersAppliedEvent() {
        creditNote.markApplied();
        assertThat(creditNote.pullDomainEvents())
            .hasSize(1)
            .first().isInstanceOf(CreditNoteAppliedEvent.class);
    }

    @Test
    void markApplied_isIdempotentWhenAlreadyApplied() {
        creditNote = creditNote.toBuilder().status(CreditNoteStatus.APPLIED).build();
        creditNote.markApplied();
        assertThat(creditNote.getStatus()).isEqualTo(CreditNoteStatus.APPLIED);
        assertThat(creditNote.pullDomainEvents()).isEmpty();
    }

    @Test
    void markApplied_throwsWhenVoided() {
        creditNote = creditNote.toBuilder().status(CreditNoteStatus.VOID).build();
        assertThatThrownBy(creditNote::markApplied)
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("Cannot apply credit note from status");
    }

    @Test
    void markVoided_transitionsFromOpen() {
        creditNote.markVoided();
        assertThat(creditNote.getStatus()).isEqualTo(CreditNoteStatus.VOID);
    }

    @Test
    void markVoided_registersVoidedEvent() {
        creditNote.markVoided();
        assertThat(creditNote.pullDomainEvents())
            .hasSize(1)
            .first().isInstanceOf(CreditNoteVoidedEvent.class);
    }

    @Test
    void markVoided_isIdempotentWhenAlreadyVoided() {
        creditNote = creditNote.toBuilder().status(CreditNoteStatus.VOID).build();
        creditNote.markVoided();
        assertThat(creditNote.getStatus()).isEqualTo(CreditNoteStatus.VOID);
        assertThat(creditNote.pullDomainEvents()).isEmpty();
    }

    @Test
    void markVoided_throwsWhenApplied() {
        creditNote = creditNote.toBuilder().status(CreditNoteStatus.APPLIED).build();
        assertThatThrownBy(creditNote::markVoided)
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("Cannot void credit note from status");
    }

    @Test
    void domainEvents_registerPullClearCycle() {
        creditNote.registerEvent("TEST_EVENT");
        assertThat(creditNote.pullDomainEvents()).containsExactly("TEST_EVENT");
        assertThat(creditNote.pullDomainEvents()).isEmpty();

        creditNote.registerEvent("ANOTHER");
        creditNote.clearDomainEvents();
        assertThat(creditNote.pullDomainEvents()).isEmpty();
    }
}
