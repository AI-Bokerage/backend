package io.genfin.ledger.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.ledger.id.JournalEntryId;
import io.genfin.ledger.id.LedgerId;

public record JournalReversed(
    EventMetadata metadata,
    LedgerId ledgerId,
    JournalEntryId originalJournalEntryId,
    JournalEntryId reversalJournalEntryId)
    implements DomainEvent {}
