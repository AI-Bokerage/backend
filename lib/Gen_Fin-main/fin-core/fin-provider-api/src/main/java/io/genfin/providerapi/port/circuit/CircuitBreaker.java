package io.genfin.providerapi.port.circuit;

import io.genfin.providerapi.circuit.CircuitMetrics;
import io.genfin.providerapi.circuit.CircuitState;

/**
 * A circuit-breaker contract — no direct dependency on Resilience4j/Failsafe. Adapters may wrap
 * either library, or this default in-memory implementation may be used as-is.
 */
public interface CircuitBreaker {

  boolean allowRequest();

  void recordSuccess();

  void recordFailure();

  CircuitState state();

  CircuitMetrics metrics();
}
