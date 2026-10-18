package io.genfin.ledger.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.id.LedgerId;

public record BalanceUpdated(EventMetadata metadata, LedgerId ledgerId, AccountId accountId)
    implements DomainEvent {}
