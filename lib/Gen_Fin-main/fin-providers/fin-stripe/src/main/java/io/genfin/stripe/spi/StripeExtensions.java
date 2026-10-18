package io.genfin.stripe.spi;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.providerapi.port.auth.AuthenticationProvider;
import io.genfin.providerapi.port.registry.ProviderFactory;
import io.genfin.providerapi.port.webhook.WebhookVerifier;
import io.genfin.stripe.auth.StripeAuthenticationProvider;
import io.genfin.stripe.config.StripeConfiguration;
import io.genfin.stripe.factory.StripeGatewayFactory;
import io.genfin.stripe.webhook.StripeWebhookVerifier;

/**
 * Registers Stripe's extensions into a {@code fin-api} {@code ExtensionRegistry}, alongside the
 * defaults from fin-payment/fin-provider-api.
 */
public final class StripeExtensions {

  private StripeExtensions() {}

  public static void register(
      ExtensionRegistry registry, StripeConfiguration configuration, String webhookSigningSecret) {
    registry.register(
        AuthenticationProvider.class, new StripeAuthenticationProvider(configuration));
    registry.register(WebhookVerifier.class, new StripeWebhookVerifier());
    registry.register(ProviderFactory.class, new StripeGatewayFactory(webhookSigningSecret));
  }
}
