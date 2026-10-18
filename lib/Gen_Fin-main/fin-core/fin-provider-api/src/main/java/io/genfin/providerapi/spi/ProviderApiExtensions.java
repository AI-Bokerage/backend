package io.genfin.providerapi.spi;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.providerapi.circuit.CircuitBreakers;
import io.genfin.providerapi.internal.idempotency.PassthroughIdempotencyMapper;
import io.genfin.providerapi.internal.webhook.InMemoryReplayProtection;
import io.genfin.providerapi.port.circuit.CircuitBreakerFactory;
import io.genfin.providerapi.port.idempotency.ProviderIdempotencyMapper;
import io.genfin.providerapi.port.registry.ProviderLoader;
import io.genfin.providerapi.port.registry.ProviderResolver;
import io.genfin.providerapi.port.registry.ProviderSelector;
import io.genfin.providerapi.port.retry.RetryPolicy;
import io.genfin.providerapi.port.webhook.WebhookReplayProtection;
import io.genfin.providerapi.registry.ProviderRegistries;
import io.genfin.providerapi.retry.RetryPolicies;

/**
 * Registers every default Provider-Platform extension so downstream code discovers them through one
 * mechanism.
 */
public final class ProviderApiExtensions {

  private ProviderApiExtensions() {}

  public static void registerDefaults(ExtensionRegistry registry) {
    registry.register(ProviderSelector.class, ProviderRegistries.highestPriority());
    registry.register(ProviderResolver.class, ProviderRegistries.standardResolver());
    registry.register(ProviderLoader.class, ProviderRegistries.standardLoader());
    registry.register(RetryPolicy.class, RetryPolicies.standard());
    registry.register(CircuitBreakerFactory.class, CircuitBreakers.standard());
    registry.register(ProviderIdempotencyMapper.class, new PassthroughIdempotencyMapper());
    registry.register(WebhookReplayProtection.class, new InMemoryReplayProtection());
  }
}
