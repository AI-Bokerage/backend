package io.genfin.ledger.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.ledger.id.LedgerId;
import io.genfin.ledger.id.PostingId;

public record PostingCompleted(EventMetadata metadata, LedgerId ledgerId, PostingId postingId)
    implements DomainEvent {}
