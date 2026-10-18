package io.genfin.razorpay.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.razorpay.auth.RazorpayAuthenticationProvider;
import io.genfin.razorpay.support.TestFixtures;
import org.junit.jupiter.api.Test;

class RazorpayConfigurationTest {

  @Test
  void exposesKeyIdAndSecretFromUnderlyingCredential() {
    var configuration = TestFixtures.razorpayConfiguration();

    assertThat(configuration.keyId()).isEqualTo("rzp_test_dummy");
    assertThat(configuration.keySecret()).isEqualTo("dummy_key_secret");
    assertThat(configuration.webhookSecret()).isEqualTo("dummy_webhook_secret");
  }

  @Test
  void authenticationProviderSuppliesBasicAuthCredential() {
    var provider = new RazorpayAuthenticationProvider(TestFixtures.razorpayConfiguration());

    assertThat(provider.credential().attributes()).containsEntry("username", "rzp_test_dummy");
  }
}
