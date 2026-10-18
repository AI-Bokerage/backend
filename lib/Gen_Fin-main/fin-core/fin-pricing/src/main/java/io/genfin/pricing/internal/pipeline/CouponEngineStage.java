package io.genfin.pricing.internal.pipeline;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.calculation.CalculationIssue;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.coupon.CouponResult;
import io.genfin.pricing.coupon.CouponValidator;
import io.genfin.pricing.pipeline.PricingStage;
import io.genfin.pricing.port.coupon.CouponPolicy;
import io.genfin.pricing.port.pipeline.PricingPipelineStage;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.pricing.PricingContext;
import java.util.ArrayList;
import java.util.List;

/**
 * The Coupon Engine stage: applies the configured {@link CouponPolicy} to every line's
 * discounted-and-promoted {@link Price}, then runs the {@link CouponValidator} over each result,
 * collecting every issue it raises rather than stopping at the first one found. Replaces the
 * structural {@code PassThroughStage} previously standing in for {@link PricingStage#COUPON}.
 */
public final class CouponEngineStage implements PricingPipelineStage {

  private final CouponPolicy policy;
  private final CouponValidator validator;

  public CouponEngineStage(CouponPolicy policy, CouponValidator validator) {
    this.policy = Validate.notNull(policy, "policy must not be null.");
    this.validator = Validate.notNull(validator, "validator must not be null.");
  }

  @Override
  public PricingStage stage() {
    return PricingStage.COUPON;
  }

  @Override
  public CalculationResult apply(PricingContext context, CalculationResult result) {
    Validate.notNull(context, "context must not be null.");
    Validate.notNull(result, "result must not be null.");
    List<CouponResult> couponResults = policy.apply(result.prices(), context.lines(), context);
    List<Price> prices = couponResults.stream().map(CouponResult::price).toList();
    List<CalculationIssue> issues = new ArrayList<>();
    for (CouponResult couponResult : couponResults) {
      issues.addAll(validator.validate(couponResult, context));
    }
    return result.withPrices(prices).addIssues(issues);
  }
}
