package io.genfin.providerapi.port.registry;

import io.genfin.api.port.spi.Extension;
import io.genfin.payment.port.gateway.PaymentGateway;
import io.genfin.providerapi.config.ProviderConfiguration;

/**
 * Constructs a {@link PaymentGateway} instance from a {@link ProviderConfiguration} — one per
 * provider module.
 */
public interface ProviderFactory extends Extension {

  PaymentGateway create(ProviderConfiguration configuration);
}
