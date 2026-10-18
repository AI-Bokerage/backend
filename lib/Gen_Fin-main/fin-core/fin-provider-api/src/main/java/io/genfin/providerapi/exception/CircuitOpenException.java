package io.genfin.providerapi.exception;

import io.genfin.api.exception.GenFinException;

public class CircuitOpenException extends GenFinException {

  public CircuitOpenException(String providerId) {
    super(ProviderApiErrorCode.CIRCUIT_OPEN, "Circuit breaker is open for provider: " + providerId);
  }
}
