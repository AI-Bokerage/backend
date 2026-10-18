package io.genfin.api.time;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.port.time.SettableClockProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ClockProviderTest {

  @Test
  void fixedClockIsDeterministicUntilAdvanced() {
    Instant initial = Instant.parse("2026-01-01T00:00:00Z");
    SettableClockProvider clock = ClockProviders.fixed(initial);

    assertThat(clock.now()).isEqualTo(initial);
    assertThat(clock.now()).isEqualTo(initial);

    Instant later = initial.plusSeconds(60);
    clock.set(later);

    assertThat(clock.now()).isEqualTo(later);
  }

  @Test
  void systemClockReturnsCurrentInstant() {
    Instant before = Instant.now();
    Instant now = ClockProviders.system().now();
    Instant after = Instant.now();

    assertThat(now).isBetween(before, after);
  }
}
