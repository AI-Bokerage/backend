package io.genfin.payment.port.gateway;

import java.util.List;
import java.util.Optional;

/**
 * Registry of configured {@link PaymentGateway}s, keyed by an opaque provider id ("stripe",
 * "razorpay", ...).
 */
public interface GatewayRegistry {

  void register(String gatewayId, PaymentGateway gateway);

  Optional<PaymentGateway> find(String gatewayId);

  List<String> registeredIds();
}
