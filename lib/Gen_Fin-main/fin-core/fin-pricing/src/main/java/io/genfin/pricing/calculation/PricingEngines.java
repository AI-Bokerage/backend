package io.genfin.pricing.calculation;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.pricing.internal.calculation.DefaultPricingEngine;
import io.genfin.pricing.port.calculation.PricingEngine;
import io.genfin.pricing.port.pipeline.PricingPipeline;

/**
 * Factory for {@link PricingEngine} instances. Mirrors {@code
 * io.genfin.ledger.posting.PostingEngines}.
 */
public final class PricingEngines {

  private PricingEngines() {}

  public static PricingEngine of(PricingPipeline pipeline) {
    return new DefaultPricingEngine(pipeline);
  }

  /**
   * Resolves the {@link PricingEngine} registered in {@code registry} if present; otherwise builds
   * one from the registered {@link PricingPipeline} (required - Gen-Fin has no default pipeline to
   * fall back to).
   */
  public static PricingEngine from(ExtensionRegistry registry) {
    return registry
        .find(PricingEngine.class)
        .orElseGet(
            () ->
                of(
                    registry
                        .find(PricingPipeline.class)
                        .orElseThrow(
                            () ->
                                new IllegalStateException(
                                    "No PricingPipeline registered - register one via "
                                        + "ExtensionRegistry before resolving a PricingEngine."))));
  }
}
