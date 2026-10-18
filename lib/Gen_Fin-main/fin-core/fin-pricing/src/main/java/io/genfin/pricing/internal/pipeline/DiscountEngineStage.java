package io.genfin.pricing.internal.pipeline;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.calculation.CalculationIssue;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.pipeline.PricingStage;
import io.genfin.pricing.port.discount.DiscountPolicy;
import io.genfin.pricing.port.discount.DiscountValidator;
import io.genfin.pricing.port.pipeline.PricingPipelineStage;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.pricing.PricingContext;
import java.util.ArrayList;
import java.util.List;

/**
 * The Discount Engine stage: applies the configured {@link DiscountPolicy} to every line's
 * base-resolved {@link Price}, then runs the {@link DiscountValidator} over each discounted result,
 * collecting every issue it raises rather than stopping at the first one found. Replaces the
 * structural {@code PassThroughStage} previously standing in for {@link PricingStage#DISCOUNT}.
 */
public final class DiscountEngineStage implements PricingPipelineStage {

  private final DiscountPolicy policy;
  private final DiscountValidator validator;

  public DiscountEngineStage(DiscountPolicy policy, DiscountValidator validator) {
    this.policy = Validate.notNull(policy, "policy must not be null.");
    this.validator = Validate.notNull(validator, "validator must not be null.");
  }

  @Override
  public PricingStage stage() {
    return PricingStage.DISCOUNT;
  }

  @Override
  public CalculationResult apply(PricingContext context, CalculationResult result) {
    Validate.notNull(context, "context must not be null.");
    Validate.notNull(result, "result must not be null.");
    List<Price> discounted = policy.apply(result.prices(), context.lines(), context);
    List<CalculationIssue> issues = new ArrayList<>();
    for (Price price : discounted) {
      issues.addAll(validator.validate(price, context));
    }
    return result.withPrices(discounted).addIssues(issues);
  }
}
