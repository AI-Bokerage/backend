package io.genfin.pricing.internal.pipeline;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.pipeline.PricingStage;
import io.genfin.pricing.port.pipeline.PricingPipelineStage;
import io.genfin.pricing.port.promotion.PromotionPolicy;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.promotion.PromotionResult;
import java.util.List;

/**
 * The Promotion Engine stage: applies the configured {@link PromotionPolicy} to every line's
 * discounted {@link Price}, crediting at most one winning campaign per line. Replaces the
 * structural {@code PassThroughStage} previously standing in for {@link PricingStage#PROMOTION}.
 */
public final class PromotionEngineStage implements PricingPipelineStage {

  private final PromotionPolicy policy;

  public PromotionEngineStage(PromotionPolicy policy) {
    this.policy = Validate.notNull(policy, "policy must not be null.");
  }

  @Override
  public PricingStage stage() {
    return PricingStage.PROMOTION;
  }

  @Override
  public CalculationResult apply(PricingContext context, CalculationResult result) {
    Validate.notNull(context, "context must not be null.");
    Validate.notNull(result, "result must not be null.");
    List<PromotionResult> promotionResults =
        policy.apply(result.prices(), context.lines(), context);
    List<Price> prices = promotionResults.stream().map(PromotionResult::price).toList();
    return result.withPrices(prices);
  }
}
