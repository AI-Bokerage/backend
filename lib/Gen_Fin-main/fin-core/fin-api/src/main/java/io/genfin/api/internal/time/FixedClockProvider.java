package io.genfin.api.internal.time;

import io.genfin.api.port.time.SettableClockProvider;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public final class FixedClockProvider implements SettableClockProvider {

  private final AtomicReference<Instant> current;

  public FixedClockProvider(Instant initial) {
    this.current = new AtomicReference<>(initial);
  }

  @Override
  public Instant now() {
    return current.get();
  }

  @Override
  public void set(Instant instant) {
    current.set(instant);
  }
}
