package io.genfin.razorpay.spi;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.providerapi.port.auth.AuthenticationProvider;
import io.genfin.providerapi.port.registry.ProviderFactory;
import io.genfin.providerapi.port.webhook.WebhookVerifier;
import io.genfin.razorpay.auth.RazorpayAuthenticationProvider;
import io.genfin.razorpay.config.RazorpayConfiguration;
import io.genfin.razorpay.factory.RazorpayGatewayFactory;
import io.genfin.razorpay.webhook.RazorpayWebhookVerifier;

/**
 * Registers Razorpay's extensions into a {@code fin-api} {@code ExtensionRegistry}, alongside the
 * defaults from fin-payment/fin-provider-api.
 */
public final class RazorpayExtensions {

  private RazorpayExtensions() {}

  public static void register(ExtensionRegistry registry, RazorpayConfiguration configuration) {
    registry.register(
        AuthenticationProvider.class, new RazorpayAuthenticationProvider(configuration));
    registry.register(WebhookVerifier.class, new RazorpayWebhookVerifier());
    registry.register(
        ProviderFactory.class,
        new RazorpayGatewayFactory(configuration.keySecret(), configuration.webhookSecret()));
  }
}
