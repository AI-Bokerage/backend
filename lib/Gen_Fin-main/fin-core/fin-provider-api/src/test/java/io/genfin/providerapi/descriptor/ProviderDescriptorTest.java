package io.genfin.providerapi.descriptor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProviderDescriptorTest {

  private static ProviderDescriptor descriptor(ProviderStatus status, int priority) {
    return new ProviderDescriptor(
        ProviderId.of("stripe"),
        new ProviderName("Stripe"),
        new ProviderVersion("1.0"),
        ProviderRegion.GLOBAL,
        ProviderEnvironment.SANDBOX,
        new ProviderPriority(priority),
        status,
        null);
  }

  @Test
  void activeAndDegradedAreUsableDisabledAndUnavailableAreNot() {
    assertThat(descriptor(ProviderStatus.ACTIVE, 0).isUsable()).isTrue();
    assertThat(descriptor(ProviderStatus.DEGRADED, 0).isUsable()).isTrue();
    assertThat(descriptor(ProviderStatus.UNAVAILABLE, 0).isUsable()).isFalse();
    assertThat(descriptor(ProviderStatus.DISABLED, 0).isUsable()).isFalse();
  }

  @Test
  void nullMetadataDefaultsToEmpty() {
    assertThat(descriptor(ProviderStatus.ACTIVE, 0).metadata().values()).isEmpty();
  }

  @Test
  void priorityOrdersNumerically() {
    assertThat(new ProviderPriority(5).compareTo(new ProviderPriority(1))).isPositive();
  }
}
