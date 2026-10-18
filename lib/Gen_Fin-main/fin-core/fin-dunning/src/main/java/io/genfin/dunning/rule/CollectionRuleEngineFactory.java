package io.genfin.dunning.rule;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.dunning.port.rule.CollectionRuleEngine;

/**
 * Resolves the {@link CollectionRuleEngine} registered in an {@link ExtensionRegistry}, falling
 * back to {@link CollectionRuleEngines#standard()}.
 */
public final class CollectionRuleEngineFactory {

  private CollectionRuleEngineFactory() {}

  public static CollectionRuleEngine from(ExtensionRegistry registry) {
    return registry.find(CollectionRuleEngine.class).orElseGet(CollectionRuleEngines::standard);
  }
}
