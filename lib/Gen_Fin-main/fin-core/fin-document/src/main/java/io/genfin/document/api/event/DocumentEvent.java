package io.genfin.document.api.event;

import java.time.Instant;

/** Marker interface for document lifecycle events. All events carry a timestamp. */
public interface DocumentEvent {

  /**
   * The moment at which this event occurred.
   *
   * @return the event's timestamp, never null
   */
  Instant occurredAt();
}
