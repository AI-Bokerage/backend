package io.genfin.dunning.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.dunning.id.DunningCaseId;

/**
 * Fired when a {@code DunningCase} closes because the obligation was written off as uncollectable.
 */
public record CollectionWrittenOff(EventMetadata metadata, DunningCaseId caseId, String reason)
    implements DomainEvent {}
