package io.genfin.reconciliation.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.reconciliation.id.DiscrepancyId;
import io.genfin.reconciliation.id.ReconciliationId;

public record DiscrepancyDetected(
    EventMetadata metadata,
    ReconciliationId reconciliationId,
    DiscrepancyId discrepancyId,
    String note)
    implements DomainEvent {}
