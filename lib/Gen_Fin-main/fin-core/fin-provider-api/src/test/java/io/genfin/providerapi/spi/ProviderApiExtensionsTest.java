package io.genfin.providerapi.spi;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.spi.ExtensionRegistries;
import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.providerapi.port.circuit.CircuitBreakerFactory;
import io.genfin.providerapi.port.idempotency.ProviderIdempotencyMapper;
import io.genfin.providerapi.port.registry.ProviderLoader;
import io.genfin.providerapi.port.registry.ProviderResolver;
import io.genfin.providerapi.port.registry.ProviderSelector;
import io.genfin.providerapi.port.retry.RetryPolicy;
import io.genfin.providerapi.port.webhook.WebhookReplayProtection;
import org.junit.jupiter.api.Test;

class ProviderApiExtensionsTest {

  @Test
  void allDefaultProviderApiExtensionsAreDiscoverableAfterRegistration() {
    ExtensionRegistry registry = ExtensionRegistries.create();

    ProviderApiExtensions.registerDefaults(registry);

    assertThat(registry.find(ProviderSelector.class)).isPresent();
    assertThat(registry.find(ProviderResolver.class)).isPresent();
    assertThat(registry.find(ProviderLoader.class)).isPresent();
    assertThat(registry.find(RetryPolicy.class)).isPresent();
    assertThat(registry.find(CircuitBreakerFactory.class)).isPresent();
    assertThat(registry.find(ProviderIdempotencyMapper.class)).isPresent();
    assertThat(registry.find(WebhookReplayProtection.class)).isPresent();
  }
}
