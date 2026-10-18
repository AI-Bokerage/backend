package io.genfin.api.port.time;

import java.time.Instant;

/** A {@link ClockProvider} whose instant can be advanced — for simulation and replay scenarios. */
public interface SettableClockProvider extends ClockProvider {

  void set(Instant instant);
}
