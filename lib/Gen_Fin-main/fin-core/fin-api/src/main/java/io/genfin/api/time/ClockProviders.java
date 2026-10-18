package io.genfin.api.time;

import io.genfin.api.internal.time.FixedClockProvider;
import io.genfin.api.internal.time.SystemClockProvider;
import io.genfin.api.port.time.ClockProvider;
import io.genfin.api.port.time.SettableClockProvider;
import java.time.Instant;

/**
 * Factory for {@link ClockProvider} instances. Consumers must obtain clocks here, not instantiate
 * directly.
 */
public final class ClockProviders {

  private static final ClockProvider SYSTEM = new SystemClockProvider();

  private ClockProviders() {}

  public static ClockProvider system() {
    return SYSTEM;
  }

  public static SettableClockProvider fixed(Instant initial) {
    return new FixedClockProvider(initial);
  }
}
