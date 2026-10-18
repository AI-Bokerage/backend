package io.genfin.payment.gateway;

import io.genfin.payment.internal.gateway.DefaultGatewayRegistry;
import io.genfin.payment.internal.gateway.FirstCapableGatewaySelector;
import io.genfin.payment.port.gateway.GatewayRegistry;
import io.genfin.payment.port.gateway.GatewaySelector;

public final class GatewayRegistries {

  private static final GatewaySelector FIRST_CAPABLE = new FirstCapableGatewaySelector();

  private GatewayRegistries() {}

  public static GatewayRegistry empty() {
    return new DefaultGatewayRegistry();
  }

  public static GatewaySelector firstCapable() {
    return FIRST_CAPABLE;
  }
}
