package io.genfin.payment.port.gateway;

import io.genfin.api.port.spi.Extension;
import io.genfin.payment.gateway.GatewayRequest;
import java.util.Optional;

/**
 * Chooses which registered gateway should handle a given request (by method, region, cost, ...).
 */
public interface GatewaySelector extends Extension {

  Optional<PaymentGateway> select(GatewayRequest request, GatewayRegistry registry);
}
