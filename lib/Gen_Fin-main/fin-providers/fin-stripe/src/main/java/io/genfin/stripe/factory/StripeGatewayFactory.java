package io.genfin.stripe.factory;

import io.genfin.payment.port.gateway.PaymentGateway;
import io.genfin.providerapi.config.ProviderConfiguration;
import io.genfin.providerapi.port.registry.ProviderFactory;
import io.genfin.stripe.config.StripeConfiguration;
import io.genfin.stripe.gateway.StripeGateway;

public final class StripeGatewayFactory implements ProviderFactory {

  private final String webhookSigningSecret;

  public StripeGatewayFactory(String webhookSigningSecret) {
    this.webhookSigningSecret = webhookSigningSecret;
  }

  @Override
  public PaymentGateway create(ProviderConfiguration configuration) {
    return new StripeGateway(StripeConfiguration.of(configuration, webhookSigningSecret));
  }
}
