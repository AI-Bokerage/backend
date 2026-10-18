package io.genfin.pricing.port.pipeline;

import io.genfin.api.port.spi.Extension;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.pipeline.PricingStage;
import io.genfin.pricing.pricing.PricingContext;

/**
 * One replaceable step of the Pricing Pipeline (Catalog Resolution, Base Price Resolution,
 * Discount/Promotion/Coupon/Credit Engines, Tax Placeholder, Rounding, Pricing Validation). Reads
 * the read-only {@link PricingContext} and the running {@link CalculationResult}, and returns the
 * next {@link CalculationResult} - never mutates either in place. Mirrors {@code
 * io.genfin.ledger.port.posting.PostingStrategy}'s per-fact resolution shape, applied instead to a
 * whole pipeline run.
 */
public interface PricingPipelineStage extends Extension {

  /** Which {@link PricingStage} this implementation performs. */
  PricingStage stage();

  CalculationResult apply(PricingContext context, CalculationResult result);
}
