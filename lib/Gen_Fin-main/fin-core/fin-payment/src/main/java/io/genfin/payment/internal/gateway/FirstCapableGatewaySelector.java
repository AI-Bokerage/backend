package io.genfin.payment.internal.gateway;

import io.genfin.payment.gateway.GatewayRequest;
import io.genfin.payment.port.gateway.GatewayRegistry;
import io.genfin.payment.port.gateway.GatewaySelector;
import io.genfin.payment.port.gateway.PaymentGateway;
import java.util.Optional;

/**
 * The simplest possible selection strategy: the first registered gateway that supports the
 * request's payment method.
 */
public final class FirstCapableGatewaySelector implements GatewaySelector {

  @Override
  public Optional<PaymentGateway> select(GatewayRequest request, GatewayRegistry registry) {
    return registry.registeredIds().stream()
        .map(registry::find)
        .flatMap(Optional::stream)
        .filter(
            gateway -> gateway.capabilities().supportedMethods().contains(request.method().type()))
        .findFirst();
  }
}
