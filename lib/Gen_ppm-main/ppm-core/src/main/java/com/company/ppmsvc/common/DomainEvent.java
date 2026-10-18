package com.company.ppmsvc.common;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Root base class for all PPM domain events.
 *
 * <p>Events are value objects — never mutated after creation.
 * Aggregates collect events via {@code BaseEntity.registerEvent()} and the
 * application layer drains and publishes them after a successful persist.
 */
@Getter
public abstract class DomainEvent {

    private final UUID eventId;
    private final UUID aggregateId;
    private final Instant occurredAt;

    protected DomainEvent(UUID aggregateId) {
        this.eventId     = UUID.randomUUID();
        this.aggregateId = aggregateId;
        this.occurredAt  = Instant.now();
    }

    public String eventType() {
        return getClass().getSimpleName();
    }

    @Override
    public String toString() {
        return eventType() + "[eventId=" + eventId
            + ", aggregateId=" + aggregateId
            + ", occurredAt=" + occurredAt + "]";
    }
}
