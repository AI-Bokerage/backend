package io.genfin.ledger.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.ledger.id.AccountingPeriodId;
import io.genfin.ledger.id.LedgerId;

public record PeriodClosed(EventMetadata metadata, LedgerId ledgerId, AccountingPeriodId periodId)
    implements DomainEvent {}
