package io.genfin.dunning.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.dunning.collection.CollectionStage;
import io.genfin.dunning.id.DunningCaseId;

/**
 * Terminal event for a {@code DunningCase}; {@code finalStage} is either COMPLETED or WRITTEN_OFF.
 */
public record DunningCaseClosed(
    EventMetadata metadata, DunningCaseId caseId, CollectionStage finalStage, String reason)
    implements DomainEvent {}
