package io.genfin.dunning.escalation;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.dunning.port.escalation.EscalationStrategy;

/**
 * Resolves the {@link EscalationStrategy} registered in an {@link ExtensionRegistry}, falling back
 * to {@link EscalationStrategies#standard()}. Mirrors {@code
 * io.genfin.dunning.rule.CollectionRuleEngineFactory}.
 */
public final class EscalationEngineFactory {

  private EscalationEngineFactory() {}

  public static EscalationStrategy from(ExtensionRegistry registry) {
    return registry.find(EscalationStrategy.class).orElseGet(EscalationStrategies::standard);
  }
}
