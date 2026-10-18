package io.genfin.dunning.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.dunning.id.DunningCaseId;
import io.genfin.dunning.id.ObligationId;

public record DunningCaseOpened(
    EventMetadata metadata, DunningCaseId caseId, ObligationId obligationId)
    implements DomainEvent {}
