package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.LedgerEntryType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class BillingLedgerEntry {
    private UUID id;
    private UUID tenantId;
    private UUID subscriptionId;
    private UUID invoiceId;
    private UUID creditNoteId;
    private LedgerEntryType entryType;
    private long amountMinor;
    private String currency;
    private String description;
    private String metadata;
    private Instant createdAt;

    @Builder.Default
    private List<Object> domainEvents = new ArrayList<>();

    public void registerEvent(Object event) {
        if (this.domainEvents == null) this.domainEvents = new ArrayList<>();
        this.domainEvents.add(event);
    }

    public List<Object> pullDomainEvents() {
        if (this.domainEvents == null || this.domainEvents.isEmpty()) return Collections.emptyList();
        List<Object> snapshot = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return snapshot;
    }

    public void clearDomainEvents() {
        if (this.domainEvents != null) this.domainEvents.clear();
    }
}
