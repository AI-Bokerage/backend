package io.genfin.dunning.retry;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.dunning.port.retry.RetryStrategy;

/**
 * Resolves the {@link RetryStrategy} registered in an {@link ExtensionRegistry}, falling back to
 * {@link RetryStrategies#standard()}. Mirrors {@code
 * io.genfin.dunning.rule.CollectionRuleEngineFactory}.
 */
public final class RetryEngineFactory {

  private RetryEngineFactory() {}

  public static RetryStrategy from(ExtensionRegistry registry) {
    return registry.find(RetryStrategy.class).orElseGet(RetryStrategies::standard);
  }
}
