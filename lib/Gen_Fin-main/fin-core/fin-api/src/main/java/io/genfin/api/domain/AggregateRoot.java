package io.genfin.api.domain;

import io.genfin.api.event.DomainEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * An {@link Entity} that is a transaction/consistency boundary and records domain events as it
 * changes.
 */
public abstract class AggregateRoot<ID> extends Entity<ID> {

  private final transient List<DomainEvent> pendingEvents = new ArrayList<>();

  protected AggregateRoot(ID id) {
    super(id);
  }

  protected final void registerEvent(DomainEvent event) {
    pendingEvents.add(event);
  }

  public final List<DomainEvent> pullEvents() {
    List<DomainEvent> events = List.copyOf(pendingEvents);
    pendingEvents.clear();
    return events;
  }
}
