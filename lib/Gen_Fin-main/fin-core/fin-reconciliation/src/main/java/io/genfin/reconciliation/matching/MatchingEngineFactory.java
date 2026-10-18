package io.genfin.reconciliation.matching;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.reconciliation.port.matching.MatchingEngine;

/**
 * Resolves the {@link MatchingEngine} registered in an {@link ExtensionRegistry}, falling back to
 * {@link MatchingEngines#standard()}.
 */
public final class MatchingEngineFactory {

  private MatchingEngineFactory() {}

  public static MatchingEngine from(ExtensionRegistry registry) {
    return registry.find(MatchingEngine.class).orElseGet(MatchingEngines::standard);
  }
}
