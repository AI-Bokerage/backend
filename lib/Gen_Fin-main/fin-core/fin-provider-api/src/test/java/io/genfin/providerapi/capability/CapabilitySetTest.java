package io.genfin.providerapi.capability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CapabilitySetTest {

  @Test
  void supportsChecksMembership() {
    CapabilitySet set = CapabilitySet.of(ProviderCapability.AUTHORIZE, ProviderCapability.CAPTURE);

    assertThat(set.supports(ProviderCapability.AUTHORIZE)).isTrue();
    assertThat(set.supports(ProviderCapability.REFUND)).isFalse();
  }

  @Test
  void supportsAllRequiresEveryCapability() {
    CapabilitySet set =
        CapabilitySet.of(
            ProviderCapability.AUTHORIZE, ProviderCapability.CAPTURE, ProviderCapability.REFUND);

    assertThat(set.supportsAll(ProviderCapability.AUTHORIZE, ProviderCapability.CAPTURE)).isTrue();
    assertThat(set.supportsAll(ProviderCapability.AUTHORIZE, ProviderCapability.UPI)).isFalse();
  }
}
