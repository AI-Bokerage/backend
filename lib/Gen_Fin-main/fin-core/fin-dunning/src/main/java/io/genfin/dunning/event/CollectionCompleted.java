package io.genfin.dunning.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.dunning.id.DunningCaseId;
import io.genfin.dunning.id.ObligationId;

/** Fired when a {@code DunningCase} closes because the obligation was satisfied. */
public record CollectionCompleted(
    EventMetadata metadata, DunningCaseId caseId, ObligationId obligationId, String note)
    implements DomainEvent {}
