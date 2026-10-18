package io.genfin.pricing.internal.pipeline;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.pipeline.PricingStage;
import io.genfin.pricing.port.pipeline.PricingPipelineStage;
import io.genfin.pricing.pricing.PricingContext;

/**
 * A no-op {@link PricingPipelineStage}: returns the {@link CalculationResult} it was given
 * unchanged. Fills the Discount/Promotion/Coupon/Credit Engines, Tax Placeholder and Rounding &amp;
 * Money Policies slots of the Pricing Pipeline until each one's real engine is implemented in a
 * later stage - keeping the pipeline structurally complete and testable end-to-end today.
 */
public final class PassThroughStage implements PricingPipelineStage {

  private final PricingStage stage;

  public PassThroughStage(PricingStage stage) {
    this.stage = Validate.notNull(stage, "stage must not be null.");
  }

  @Override
  public PricingStage stage() {
    return stage;
  }

  @Override
  public CalculationResult apply(PricingContext context, CalculationResult result) {
    Validate.notNull(context, "context must not be null.");
    Validate.notNull(result, "result must not be null.");
    return result;
  }
}
