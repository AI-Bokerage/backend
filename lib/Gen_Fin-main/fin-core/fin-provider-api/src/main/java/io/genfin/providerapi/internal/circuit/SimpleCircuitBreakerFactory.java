package io.genfin.providerapi.internal.circuit;

import io.genfin.providerapi.port.circuit.CircuitBreaker;
import io.genfin.providerapi.port.circuit.CircuitBreakerFactory;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SimpleCircuitBreakerFactory implements CircuitBreakerFactory {

  private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();
  private final int failureThreshold;
  private final Duration openDuration;

  public SimpleCircuitBreakerFactory(int failureThreshold, Duration openDuration) {
    this.failureThreshold = failureThreshold;
    this.openDuration = openDuration;
  }

  @Override
  public CircuitBreaker create(String key) {
    return breakers.computeIfAbsent(
        key, k -> new SimpleCircuitBreaker(failureThreshold, openDuration));
  }
}
