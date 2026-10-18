package com.company.bsmsvc.domain.event;

import com.company.bsmsvc.domain.enums.LedgerEntryType;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryCreatedEvent(
    UUID ledgerEntryId,
    UUID tenantId,
    LedgerEntryType entryType,
    long amountMinor,
    Instant occurredAt
) {}
