package io.genfin.payment.gateway;

import io.genfin.api.config.CoreConfiguration;

/**
 * Provider-specific settings (API keys, endpoints, ...) — kept as an opaque {@code fin-api}
 * configuration bag, never typed here.
 */
public record GatewayConfiguration(CoreConfiguration settings) {

  public static GatewayConfiguration empty() {
    return new GatewayConfiguration(CoreConfiguration.empty());
  }
}
