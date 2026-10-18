package io.genfin.autoconfigure.payment;

import io.genfin.payment.gateway.GatewayRequest;
import io.genfin.payment.port.gateway.GatewayRegistry;
import io.genfin.payment.port.gateway.GatewaySelector;
import io.genfin.payment.port.gateway.PaymentGateway;
import java.util.Optional;

/**
 * Implements fin-payment's existing {@link GatewaySelector} extension point: prefers the gateway
 * registered under {@code preferredProviderId} when it's present and capable of handling the
 * request, otherwise falls back to {@code fallback} (normally {@code
 * GatewayRegistries.firstCapable()}). fin-payment itself has no select-by-name concept — this class
 * exists only because {@code genfin.payment.default-provider} needs one.
 */
final class NamedGatewaySelector implements GatewaySelector {

  private final String preferredProviderId;
  private final GatewaySelector fallback;

  NamedGatewaySelector(String preferredProviderId, GatewaySelector fallback) {
    this.preferredProviderId = preferredProviderId;
    this.fallback = fallback;
  }

  @Override
  public Optional<PaymentGateway> select(GatewayRequest request, GatewayRegistry registry) {
    Optional<PaymentGateway> preferred =
        registry
            .find(preferredProviderId)
            .filter(
                gateway ->
                    gateway.capabilities().supportedMethods().contains(request.method().type()));
    return preferred.isPresent() ? preferred : fallback.select(request, registry);
  }
}
