package io.genfin.providerapi.internal.circuit;

import io.genfin.providerapi.circuit.CircuitMetrics;
import io.genfin.providerapi.circuit.CircuitState;
import io.genfin.providerapi.port.circuit.CircuitBreaker;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Opens after {@code failureThreshold} consecutive failures; half-opens after {@code openDuration};
 * closes on the next success.
 */
public final class SimpleCircuitBreaker implements CircuitBreaker {

  private final int failureThreshold;
  private final Duration openDuration;
  private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
  private final AtomicLong consecutiveFailures = new AtomicLong();
  private final AtomicLong successCount = new AtomicLong();
  private final AtomicLong failureCount = new AtomicLong();
  private volatile Instant openedAt;
  private volatile Instant lastFailureAt;

  public SimpleCircuitBreaker(int failureThreshold, Duration openDuration) {
    this.failureThreshold = failureThreshold;
    this.openDuration = openDuration;
  }

  @Override
  public boolean allowRequest() {
    if (state.get() == CircuitState.OPEN) {
      if (openedAt != null && Instant.now().isAfter(openedAt.plus(openDuration))) {
        state.set(CircuitState.HALF_OPEN);
        return true;
      }
      return false;
    }
    return true;
  }

  @Override
  public void recordSuccess() {
    successCount.incrementAndGet();
    consecutiveFailures.set(0);
    state.set(CircuitState.CLOSED);
  }

  @Override
  public void recordFailure() {
    failureCount.incrementAndGet();
    lastFailureAt = Instant.now();
    if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
      state.set(CircuitState.OPEN);
      openedAt = Instant.now();
    }
  }

  @Override
  public CircuitState state() {
    return state.get();
  }

  @Override
  public CircuitMetrics metrics() {
    return new CircuitMetrics(successCount.get(), failureCount.get(), lastFailureAt);
  }
}
