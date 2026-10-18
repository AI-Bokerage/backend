package io.genfin.reconciliation.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.reconciliation.id.ReconciliationId;

public record MatchingCompleted(
    EventMetadata metadata, ReconciliationId reconciliationId, int matchCount)
    implements DomainEvent {}
