package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.CreditNoteStatus;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CreditNote {
    private UUID id;
    private UUID tenantId;
    private UUID invoiceId;
    private String creditNumber;
    private long amountMinor;
    private String currency;
    private String reason;
    private CreditNoteStatus status;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    @Builder.Default
    private List<Object> domainEvents = new ArrayList<>();

    public void registerEvent(Object event) {
        if (this.domainEvents == null) this.domainEvents = new ArrayList<>();
        this.domainEvents.add(event);
    }

    public List<Object> pullDomainEvents() {
        if (this.domainEvents == null || this.domainEvents.isEmpty()) return java.util.Collections.emptyList();
        List<Object> snapshot = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return snapshot;
    }

    public void clearDomainEvents() { if (this.domainEvents != null) this.domainEvents.clear(); }

    // Domain transitions
    public void markApplied() {
        if (this.status == CreditNoteStatus.APPLIED) return;
        if (this.status != CreditNoteStatus.OPEN) {
            throw new BusinessRuleViolationException("Cannot apply credit note from status: " + this.status);
        }
        this.status = CreditNoteStatus.APPLIED;
        this.updatedAt = Instant.now();
        registerEvent(new com.company.bsmsvc.domain.event.CreditNoteAppliedEvent(this.id, this.tenantId, this.invoiceId, Instant.now()));
    }

    public void markVoided() {
        if (this.status == CreditNoteStatus.VOID) return;
        if (this.status != CreditNoteStatus.OPEN) {
            throw new BusinessRuleViolationException("Cannot void credit note from status: " + this.status);
        }
        this.status = CreditNoteStatus.VOID;
        this.updatedAt = Instant.now();
        registerEvent(new com.company.bsmsvc.domain.event.CreditNoteVoidedEvent(this.id, this.tenantId, Instant.now()));
    }
}
