package io.genfin.razorpay.support;

import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.providerapi.auth.Credential;
import io.genfin.providerapi.capability.CapabilitySet;
import io.genfin.providerapi.config.ProviderConfiguration;
import io.genfin.providerapi.descriptor.ProviderDescriptor;
import io.genfin.providerapi.descriptor.ProviderEnvironment;
import io.genfin.providerapi.descriptor.ProviderId;
import io.genfin.providerapi.descriptor.ProviderName;
import io.genfin.providerapi.descriptor.ProviderPriority;
import io.genfin.providerapi.descriptor.ProviderRegion;
import io.genfin.providerapi.descriptor.ProviderStatus;
import io.genfin.providerapi.descriptor.ProviderVersion;
import io.genfin.razorpay.config.RazorpayConfiguration;

public final class TestFixtures {

  public static final Currency INR =
      CurrencyFactory.newCurrency()
          .code("INR")
          .symbol("₹")
          .displayName("Indian Rupee")
          .fractionDigits(2)
          .build();

  private TestFixtures() {}

  public static RazorpayConfiguration razorpayConfiguration() {
    ProviderDescriptor descriptor =
        new ProviderDescriptor(
            ProviderId.of("razorpay"),
            new ProviderName("Razorpay"),
            new ProviderVersion("1.0"),
            ProviderRegion.GLOBAL,
            ProviderEnvironment.SANDBOX,
            ProviderPriority.DEFAULT,
            ProviderStatus.ACTIVE,
            null);
    ProviderConfiguration base =
        ProviderConfiguration.builder()
            .descriptor(descriptor)
            .credential(Credential.apiKey("rzp_test_dummy"))
            .capabilities(new CapabilitySet(java.util.Set.of()))
            .build();
    return RazorpayConfiguration.of(base, "dummy_key_secret", "dummy_webhook_secret");
  }
}
