package io.genfin.ledger.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.ledger.id.BalanceSnapshotId;
import io.genfin.ledger.id.LedgerId;

public record SnapshotCreated(
    EventMetadata metadata, LedgerId ledgerId, BalanceSnapshotId snapshotId)
    implements DomainEvent {}
