package io.genfin.razorpay.factory;

import io.genfin.payment.port.gateway.PaymentGateway;
import io.genfin.providerapi.config.ProviderConfiguration;
import io.genfin.providerapi.port.registry.ProviderFactory;
import io.genfin.razorpay.config.RazorpayConfiguration;
import io.genfin.razorpay.gateway.RazorpayGateway;

public final class RazorpayGatewayFactory implements ProviderFactory {

  private final String keySecret;
  private final String webhookSecret;

  public RazorpayGatewayFactory(String keySecret, String webhookSecret) {
    this.keySecret = keySecret;
    this.webhookSecret = webhookSecret;
  }

  @Override
  public PaymentGateway create(ProviderConfiguration configuration) {
    return new RazorpayGateway(RazorpayConfiguration.of(configuration, keySecret, webhookSecret));
  }
}
