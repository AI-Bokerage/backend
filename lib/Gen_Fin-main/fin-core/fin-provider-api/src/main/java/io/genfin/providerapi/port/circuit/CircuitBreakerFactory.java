package io.genfin.providerapi.port.circuit;

import io.genfin.api.port.spi.Extension;

public interface CircuitBreakerFactory extends Extension {

  CircuitBreaker create(String key);
}
