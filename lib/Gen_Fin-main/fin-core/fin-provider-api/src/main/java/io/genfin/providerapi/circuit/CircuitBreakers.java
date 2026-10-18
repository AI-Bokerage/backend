package io.genfin.providerapi.circuit;

import io.genfin.providerapi.internal.circuit.SimpleCircuitBreakerFactory;
import io.genfin.providerapi.port.circuit.CircuitBreakerFactory;
import java.time.Duration;

public final class CircuitBreakers {

  private CircuitBreakers() {}

  public static CircuitBreakerFactory standard() {
    return new SimpleCircuitBreakerFactory(5, Duration.ofSeconds(30));
  }

  public static CircuitBreakerFactory of(int failureThreshold, Duration openDuration) {
    return new SimpleCircuitBreakerFactory(failureThreshold, openDuration);
  }
}
