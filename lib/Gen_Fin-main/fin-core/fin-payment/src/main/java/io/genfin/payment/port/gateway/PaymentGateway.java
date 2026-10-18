package io.genfin.payment.port.gateway;

import io.genfin.api.port.spi.Extension;
import io.genfin.payment.gateway.GatewayCapabilities;
import io.genfin.payment.gateway.GatewayHealth;
import io.genfin.payment.gateway.GatewayRequest;
import io.genfin.payment.gateway.GatewayResponse;
import io.genfin.payment.gateway.PaymentProvider;

/**
 * The interface every payment provider module (fin-stripe, fin-razorpay, ...) implements. No
 * provider SDK types, HTTP concepts, or raw JSON ever cross this boundary in either direction.
 */
public interface PaymentGateway extends Extension {

  GatewayResponse authorize(GatewayRequest request);

  GatewayResponse capture(GatewayRequest request);

  GatewayResponse voidAuthorization(GatewayRequest request);

  GatewayResponse refund(GatewayRequest request);

  GatewayCapabilities capabilities();

  GatewayHealth health();

  PaymentProvider provider();
}
