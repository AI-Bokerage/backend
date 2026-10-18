package io.genfin.stripe.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.providerapi.capability.CaptureMode;
import io.genfin.stripe.auth.StripeAuthenticationProvider;
import io.genfin.stripe.support.TestFixtures;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class StripeConfigurationTest {

  @Test
  void exposesSecretKeyFromUnderlyingCredential() {
    var configuration = TestFixtures.stripeConfiguration();

    assertThat(configuration.secretKey()).isEqualTo("sk_test_dummy");
    assertThat(configuration.webhookSigningSecret()).isEqualTo("whsec_test_dummy");
  }

  @Test
  void authenticationProviderSuppliesApiKeyCredential() {
    var provider = new StripeAuthenticationProvider(TestFixtures.stripeConfiguration());

    assertThat(provider.credential().attributes()).containsEntry("apiKey", "sk_test_dummy");
  }

  @Test
  void defaultFactoryMethodDefaultsToManualCaptureAndConfirmationWithNoApiVersion() {
    var configuration = TestFixtures.stripeConfiguration();

    assertThat(configuration.captureMode()).isEqualTo(CaptureMode.MANUAL);
    assertThat(configuration.confirmationMethod()).isEqualTo(ConfirmationMethod.MANUAL);
    assertThat(configuration.apiVersion()).isNull();
    assertThat(configuration.statementDescriptor()).isNull();
  }

  @Test
  void fullFactoryMethodCarriesEveryExplicitlySetField() {
    var configuration =
        TestFixtures.stripeConfiguration(
            CaptureMode.AUTOMATIC,
            ConfirmationMethod.AUTOMATIC,
            "2024-06-20",
            Duration.ofSeconds(5));

    assertThat(configuration.captureMode()).isEqualTo(CaptureMode.AUTOMATIC);
    assertThat(configuration.confirmationMethod()).isEqualTo(ConfirmationMethod.AUTOMATIC);
    assertThat(configuration.apiVersion()).isEqualTo("2024-06-20");
    assertThat(configuration.base().timeout()).isEqualTo(Duration.ofSeconds(5));
  }
}
