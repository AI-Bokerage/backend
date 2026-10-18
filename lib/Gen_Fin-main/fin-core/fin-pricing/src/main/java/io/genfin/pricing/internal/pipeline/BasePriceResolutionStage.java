package io.genfin.pricing.internal.pipeline;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.pipeline.PricingStage;
import io.genfin.pricing.port.calculation.PricingPolicy;
import io.genfin.pricing.port.pipeline.PricingPipelineStage;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.pricing.PricingContext;
import java.util.List;

/**
 * The Base Price Resolution stage: resolves the base {@link Price} of every line in the running
 * {@link PricingContext} via the configured {@link PricingPolicy}, and carries the result forward
 * on the {@link CalculationResult}.
 */
public final class BasePriceResolutionStage implements PricingPipelineStage {

  private final PricingPolicy policy;

  public BasePriceResolutionStage(PricingPolicy policy) {
    this.policy = Validate.notNull(policy, "policy must not be null.");
  }

  @Override
  public PricingStage stage() {
    return PricingStage.BASE_PRICE_RESOLUTION;
  }

  @Override
  public CalculationResult apply(PricingContext context, CalculationResult result) {
    Validate.notNull(context, "context must not be null.");
    Validate.notNull(result, "result must not be null.");
    List<Price> prices = policy.resolve(context.lines(), context);
    return result.withPrices(prices);
  }
}
