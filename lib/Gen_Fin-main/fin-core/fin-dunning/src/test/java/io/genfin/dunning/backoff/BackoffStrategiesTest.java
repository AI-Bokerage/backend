package io.genfin.dunning.backoff;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.dunning.port.backoff.BackoffStrategy;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class BackoffStrategiesTest {

  private static final BackoffInterval ONE_DAY = BackoffInterval.of(1, ChronoUnit.DAYS);

  @Test
  void fixedBackoffReturnsTheSameIntervalForEveryAttempt() {
    BackoffStrategy fixed = BackoffStrategies.fixed(ONE_DAY);

    assertThat(fixed.nextDelay(1)).isEqualTo(Duration.ofDays(1));
    assertThat(fixed.nextDelay(5)).isEqualTo(Duration.ofDays(1));
  }

  @Test
  void linearBackoffScalesByAttemptNumber() {
    BackoffStrategy linear = BackoffStrategies.linear(ONE_DAY);

    assertThat(linear.nextDelay(1)).isEqualTo(Duration.ofDays(1));
    assertThat(linear.nextDelay(3)).isEqualTo(Duration.ofDays(3));
  }

  @Test
  void exponentialBackoffScalesByTheConfiguredMultiplier() {
    BackoffStrategy exponential = BackoffStrategies.exponential(ONE_DAY, 2.0);

    assertThat(exponential.nextDelay(1)).isEqualTo(Duration.ofDays(1));
    assertThat(exponential.nextDelay(2)).isEqualTo(Duration.ofDays(2));
    assertThat(exponential.nextDelay(3)).isEqualTo(Duration.ofDays(4));
  }

  @Test
  void fibonacciBackoffFollowsTheFibonacciSequence() {
    BackoffStrategy fibonacci = BackoffStrategies.fibonacci(ONE_DAY);

    assertThat(fibonacci.nextDelay(1)).isEqualTo(Duration.ofDays(1));
    assertThat(fibonacci.nextDelay(2)).isEqualTo(Duration.ofDays(1));
    assertThat(fibonacci.nextDelay(3)).isEqualTo(Duration.ofDays(2));
    assertThat(fibonacci.nextDelay(4)).isEqualTo(Duration.ofDays(3));
    assertThat(fibonacci.nextDelay(5)).isEqualTo(Duration.ofDays(5));
  }

  @Test
  void anApplicationCanPlugInACustomBackoffStrategyDirectly() {
    BackoffStrategy custom = attemptNumber -> Duration.ofMinutes(attemptNumber * 15L);

    assertThat(custom.nextDelay(4)).isEqualTo(Duration.ofMinutes(60));
  }

  @Test
  void arbitraryTimeUnitsAreSupportedNotJustDays() {
    BackoffStrategy hourly = BackoffStrategies.fixed(BackoffInterval.of(6, ChronoUnit.HOURS));
    BackoffStrategy weekly = BackoffStrategies.fixed(BackoffInterval.of(2, ChronoUnit.WEEKS));

    assertThat(hourly.nextDelay(1)).isEqualTo(Duration.ofHours(6));
    assertThat(weekly.nextDelay(1)).isEqualTo(Duration.ofDays(14));
  }
}
