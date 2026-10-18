package io.genfin.dunning.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.dunning.id.DunningCaseId;
import io.genfin.dunning.id.ObligationId;

/** Fired when a {@code DunningCase} moves from opened to actively being worked. */
public record DunningStarted(
    EventMetadata metadata, DunningCaseId caseId, ObligationId obligationId)
    implements DomainEvent {}
