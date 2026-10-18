package io.genfin.api.event;

import io.genfin.api.id.AggregateId;
import io.genfin.api.id.CorrelationId;
import io.genfin.api.id.EventId;

/** Envelope metadata carried by every {@link DomainEvent}. */
public record EventMetadata(
    EventId eventId, OccurredAt occurredAt, CorrelationId correlationId, AggregateId aggregateId) {}
