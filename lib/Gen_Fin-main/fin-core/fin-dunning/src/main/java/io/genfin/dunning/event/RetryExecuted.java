package io.genfin.dunning.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.dunning.id.DunningCaseId;
import io.genfin.dunning.id.RetryId;
import io.genfin.dunning.retry.RetryResult;

/**
 * Fired when the consuming application reports back the outcome of an attempt fin-dunning only
 * planned - fin-dunning never performs the retry itself.
 */
public record RetryExecuted(
    EventMetadata metadata,
    DunningCaseId caseId,
    RetryId retryId,
    int attemptNumber,
    RetryResult result)
    implements DomainEvent {}
