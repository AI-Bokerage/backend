package io.genfin.reconciliation.rule;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.reconciliation.port.rule.RuleEngine;

/**
 * Resolves the {@link RuleEngine} registered in an {@link ExtensionRegistry}, falling back to
 * {@link RuleEngines#standard()}.
 */
public final class RuleEngineFactory {

  private RuleEngineFactory() {}

  public static RuleEngine from(ExtensionRegistry registry) {
    return registry.find(RuleEngine.class).orElseGet(RuleEngines::standard);
  }
}
