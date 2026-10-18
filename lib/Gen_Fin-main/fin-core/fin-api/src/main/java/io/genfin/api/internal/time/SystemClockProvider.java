package io.genfin.api.internal.time;

import io.genfin.api.port.time.ClockProvider;
import java.time.Instant;

public final class SystemClockProvider implements ClockProvider {

  @Override
  public Instant now() {
    return Instant.now();
  }
}
