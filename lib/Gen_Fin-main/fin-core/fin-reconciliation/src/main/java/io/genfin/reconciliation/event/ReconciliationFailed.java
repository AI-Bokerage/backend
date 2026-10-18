package io.genfin.reconciliation.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.reconciliation.id.ReconciliationId;

public record ReconciliationFailed(
    EventMetadata metadata, ReconciliationId reconciliationId, String reason)
    implements DomainEvent {}
