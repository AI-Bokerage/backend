package io.genfin.stripe.support;

import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.providerapi.auth.Credential;
import io.genfin.providerapi.capability.CapabilitySet;
import io.genfin.providerapi.capability.CaptureMode;
import io.genfin.providerapi.config.ProviderConfiguration;
import io.genfin.providerapi.descriptor.ProviderDescriptor;
import io.genfin.providerapi.descriptor.ProviderEnvironment;
import io.genfin.providerapi.descriptor.ProviderId;
import io.genfin.providerapi.descriptor.ProviderName;
import io.genfin.providerapi.descriptor.ProviderPriority;
import io.genfin.providerapi.descriptor.ProviderRegion;
import io.genfin.providerapi.descriptor.ProviderStatus;
import io.genfin.providerapi.descriptor.ProviderVersion;
import io.genfin.stripe.config.ConfirmationMethod;
import io.genfin.stripe.config.StripeConfiguration;
import java.time.Duration;

public final class TestFixtures {

  public static final Currency USD =
      CurrencyFactory.newCurrency()
          .code("USD")
          .symbol("$")
          .displayName("US Dollar")
          .fractionDigits(2)
          .build();

  private TestFixtures() {}

  public static StripeConfiguration stripeConfiguration() {
    return StripeConfiguration.of(
        providerConfiguration(Duration.ofSeconds(30)), "whsec_test_dummy");
  }

  /**
   * A configuration with an explicit capture mode, confirmation method, API version and timeout —
   * so tests can assert those values actually flow through into Stripe's own request objects
   * instead of only exercising the all-defaults fixture above.
   */
  public static StripeConfiguration stripeConfiguration(
      CaptureMode captureMode,
      ConfirmationMethod confirmationMethod,
      String apiVersion,
      Duration timeout) {
    return StripeConfiguration.of(
        providerConfiguration(timeout),
        "whsec_test_dummy",
        captureMode,
        confirmationMethod,
        apiVersion);
  }

  private static ProviderConfiguration providerConfiguration(Duration timeout) {
    ProviderDescriptor descriptor =
        new ProviderDescriptor(
            ProviderId.of("stripe"),
            new ProviderName("Stripe"),
            new ProviderVersion("1.0"),
            ProviderRegion.GLOBAL,
            ProviderEnvironment.SANDBOX,
            ProviderPriority.DEFAULT,
            ProviderStatus.ACTIVE,
            null);
    return ProviderConfiguration.builder()
        .descriptor(descriptor)
        .credential(Credential.apiKey("sk_test_dummy"))
        .capabilities(new CapabilitySet(java.util.Set.of()))
        .timeout(timeout)
        .build();
  }
}
