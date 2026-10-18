package io.genfin.providerapi.internal.registry;

import io.genfin.payment.gateway.GatewayRequest;
import io.genfin.payment.port.gateway.PaymentGateway;
import io.genfin.providerapi.port.registry.ProviderRegistry;
import io.genfin.providerapi.port.registry.ProviderResolver;
import io.genfin.providerapi.port.registry.ProviderSelector;
import java.util.Optional;

public final class DefaultProviderResolver implements ProviderResolver {

  private final ProviderSelector selector;

  public DefaultProviderResolver(ProviderSelector selector) {
    this.selector = selector;
  }

  @Override
  public Optional<PaymentGateway> resolve(GatewayRequest request, ProviderRegistry registry) {
    return selector
        .select(registry.findAll())
        .flatMap(descriptor -> registry.findGateway(descriptor.id()));
  }
}
