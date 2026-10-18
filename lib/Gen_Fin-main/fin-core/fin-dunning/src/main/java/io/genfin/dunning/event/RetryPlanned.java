package io.genfin.dunning.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.dunning.id.DunningCaseId;
import io.genfin.dunning.id.RetryId;
import java.time.Instant;

/**
 * Fired when a {@code RetryCalculator} schedules a not-yet-exhausted attempt from the resolved
 * {@code RetryPlan}. {@code scheduledAt} is the earliest instant of that attempt's
 * calendar-adjusted {@code RetryWindow}. Data only - deciding to retry never executes it.
 */
public record RetryPlanned(
    EventMetadata metadata,
    DunningCaseId caseId,
    RetryId retryId,
    int attemptNumber,
    Instant scheduledAt)
    implements DomainEvent {}
