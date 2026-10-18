package io.genfin.dunning.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.dunning.id.DunningCaseId;

/**
 * Fired when the collection effort itself fails without the case reaching a terminal {@code
 * CollectionStage} (e.g. every retry from the resolved {@code RetryPlan} was exhausted with no
 * resolution). Distinct from {@link CollectionWrittenOff}: the application decides separately
 * whether a failed case is later written off.
 */
public record CollectionFailed(EventMetadata metadata, DunningCaseId caseId, String reason)
    implements DomainEvent {}
