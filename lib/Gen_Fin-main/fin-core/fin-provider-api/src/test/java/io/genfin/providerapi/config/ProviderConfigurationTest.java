package io.genfin.providerapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.providerapi.auth.Credential;
import io.genfin.providerapi.capability.CapabilitySet;
import io.genfin.providerapi.capability.ProviderCapability;
import io.genfin.providerapi.descriptor.ProviderDescriptor;
import io.genfin.providerapi.descriptor.ProviderEnvironment;
import io.genfin.providerapi.descriptor.ProviderId;
import io.genfin.providerapi.descriptor.ProviderName;
import io.genfin.providerapi.descriptor.ProviderPriority;
import io.genfin.providerapi.descriptor.ProviderRegion;
import io.genfin.providerapi.descriptor.ProviderStatus;
import io.genfin.providerapi.descriptor.ProviderVersion;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProviderConfigurationTest {

  @Test
  void buildsWithExplicitFieldsAndDefaults() {
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

    ProviderConfiguration configuration =
        ProviderConfiguration.builder()
            .descriptor(descriptor)
            .credential(Credential.apiKey("sk_test"))
            .capabilities(
                CapabilitySet.of(ProviderCapability.AUTHORIZE, ProviderCapability.CAPTURE))
            .build();

    assertThat(configuration.descriptor()).isEqualTo(descriptor);
    assertThat(configuration.credential().scheme().name()).isEqualTo("API_KEY");
    assertThat(configuration.timeout()).isEqualTo(Duration.ofSeconds(30));
    assertThat(configuration.retryPolicy()).isNotNull();
    assertThat(configuration.capabilities().supports(ProviderCapability.AUTHORIZE)).isTrue();
  }
}
