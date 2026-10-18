package io.genfin.api.event;

import io.genfin.api.port.time.ClockProvider;
import java.time.Instant;

/** The instant a domain event occurred, distinct from when it was persisted or published. */
public record OccurredAt(Instant value) {

  public static OccurredAt now(ClockProvider clockProvider) {
    return new OccurredAt(clockProvider.now());
  }
}
