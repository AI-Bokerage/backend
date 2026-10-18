package io.genfin.ledger.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.ledger.id.LedgerId;

public record LedgerCreated(EventMetadata metadata, LedgerId ledgerId) implements DomainEvent {}
