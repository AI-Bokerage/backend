package io.genfin.providerapi.circuit;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.providerapi.port.circuit.CircuitBreaker;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CircuitBreakerTest {

  @Test
  void opensAfterConsecutiveFailuresAndBlocksRequests() {
    CircuitBreaker breaker = CircuitBreakers.of(3, Duration.ofMinutes(1)).create("provider-a");

    assertThat(breaker.allowRequest()).isTrue();
    breaker.recordFailure();
    breaker.recordFailure();
    breaker.recordFailure();

    assertThat(breaker.state()).isEqualTo(CircuitState.OPEN);
    assertThat(breaker.allowRequest()).isFalse();
  }

  @Test
  void successResetsConsecutiveFailureCountAndClosesCircuit() {
    CircuitBreaker breaker = CircuitBreakers.of(3, Duration.ofMinutes(1)).create("provider-b");

    breaker.recordFailure();
    breaker.recordFailure();
    breaker.recordSuccess();
    breaker.recordFailure();
    breaker.recordFailure();

    assertThat(breaker.state()).isEqualTo(CircuitState.CLOSED);
  }

  @Test
  void factoryReturnsTheSameBreakerInstancePerKey() {
    var factory = CircuitBreakers.standard();

    assertThat(factory.create("same-key")).isSameAs(factory.create("same-key"));
  }

  @Test
  void metricsTrackSuccessAndFailureCounts() {
    CircuitBreaker breaker = CircuitBreakers.standard().create("provider-c");

    breaker.recordSuccess();
    breaker.recordSuccess();
    breaker.recordFailure();

    var metrics = breaker.metrics();
    assertThat(metrics.successCount()).isEqualTo(2);
    assertThat(metrics.failureCount()).isEqualTo(1);
  }
}
