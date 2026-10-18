package io.genfin.api.port.time;

import java.time.Instant;

/** Extension point for obtaining the current instant. Never call {@code Instant.now()} directly. */
public interface ClockProvider {

  Instant now();
}
