package io.genfin.stripe.auth;

import io.genfin.providerapi.auth.Credential;
import io.genfin.providerapi.port.auth.AuthenticationProvider;
import io.genfin.stripe.config.StripeConfiguration;

public final class StripeAuthenticationProvider implements AuthenticationProvider {

  private final StripeConfiguration configuration;

  public StripeAuthenticationProvider(StripeConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public Credential credential() {
    return Credential.apiKey(configuration.secretKey());
  }
}
