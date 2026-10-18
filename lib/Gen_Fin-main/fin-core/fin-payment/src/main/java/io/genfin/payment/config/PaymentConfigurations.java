package io.genfin.payment.config;

import io.genfin.payment.authorization.AuthorizationStrategies;
import io.genfin.payment.gateway.GatewayRegistries;
import io.genfin.payment.lifecycle.PaymentLifecycles;
import io.genfin.payment.method.PaymentMethodRegistries;

public final class PaymentConfigurations {

  private PaymentConfigurations() {}

  public static PaymentConfiguration standard() {
    return PaymentConfiguration.builder()
        .lifecycleProvider(PaymentLifecycles.standard())
        .gatewayRegistry(GatewayRegistries.empty())
        .gatewaySelector(GatewayRegistries.firstCapable())
        .methodRegistry(
            PaymentMethodRegistries.withProvider(PaymentMethodRegistries.standardCatalog()))
        .captureStrategy(AuthorizationStrategies.automaticCapture())
        .authorizationStrategy(AuthorizationStrategies.fullAuthorization())
        .build();
  }
}
