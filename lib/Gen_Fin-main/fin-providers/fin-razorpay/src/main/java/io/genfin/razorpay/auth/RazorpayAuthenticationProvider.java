package io.genfin.razorpay.auth;

import io.genfin.providerapi.auth.Credential;
import io.genfin.providerapi.port.auth.AuthenticationProvider;
import io.genfin.razorpay.config.RazorpayConfiguration;

public final class RazorpayAuthenticationProvider implements AuthenticationProvider {

  private final RazorpayConfiguration configuration;

  public RazorpayAuthenticationProvider(RazorpayConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public Credential credential() {
    return Credential.basicAuth(configuration.keyId(), configuration.keySecret());
  }
}
