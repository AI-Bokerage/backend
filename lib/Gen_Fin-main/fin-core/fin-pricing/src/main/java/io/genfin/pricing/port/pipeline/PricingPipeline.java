package io.genfin.pricing.port.pipeline;

import io.genfin.api.port.spi.Extension;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.pricing.PricingContext;

/**
 * Runs an ordered list of {@link PricingPipelineStage}s over a {@link PricingContext}, threading
 * one {@link CalculationResult} through all of them, and returns the final result. The single SPI
 * entry point {@link io.genfin.pricing.port.calculation.PricingEngine} depends on instead of
 * running stages itself.
 */
public interface PricingPipeline extends Extension {

  CalculationResult run(PricingContext context);
}
