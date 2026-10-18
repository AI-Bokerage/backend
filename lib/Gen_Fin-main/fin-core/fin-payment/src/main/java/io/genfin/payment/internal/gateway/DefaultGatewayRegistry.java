package io.genfin.payment.internal.gateway;

import io.genfin.payment.port.gateway.GatewayRegistry;
import io.genfin.payment.port.gateway.PaymentGateway;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DefaultGatewayRegistry implements GatewayRegistry {

  private final ConcurrentMap<String, PaymentGateway> gateways = new ConcurrentHashMap<>();

  @Override
  public void register(String gatewayId, PaymentGateway gateway) {
    gateways.put(gatewayId, gateway);
  }

  @Override
  public Optional<PaymentGateway> find(String gatewayId) {
    return Optional.ofNullable(gateways.get(gatewayId));
  }

  @Override
  public List<String> registeredIds() {
    return List.copyOf(gateways.keySet());
  }
}
