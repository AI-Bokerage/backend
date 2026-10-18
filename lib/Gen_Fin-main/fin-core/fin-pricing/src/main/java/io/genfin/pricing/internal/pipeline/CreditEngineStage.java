package io.genfin.pricing.internal.pipeline;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.calculation.CalculationIssue;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.credit.CreditResult;
import io.genfin.pricing.credit.CreditValidator;
import io.genfin.pricing.pipeline.PricingStage;
import io.genfin.pricing.port.credit.CreditPolicy;
import io.genfin.pricing.port.pipeline.PricingPipelineStage;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.pricing.PricingContext;
import java.util.ArrayList;
import java.util.List;

/**
 * The Credit Engine stage: applies the configured {@link CreditPolicy} to every line's
 * discounted/promoted/coupon-applied {@link Price}, then runs the {@link CreditValidator} over each
 * result, collecting every issue it raises rather than stopping at the first one found. Replaces
 * the structural {@code PassThroughStage} previously standing in for {@link PricingStage#CREDIT}.
 */
public final class CreditEngineStage implements PricingPipelineStage {

  private final CreditPolicy policy;
  private final CreditValidator validator;

  public CreditEngineStage(CreditPolicy policy, CreditValidator validator) {
    this.policy = Validate.notNull(policy, "policy must not be null.");
    this.validator = Validate.notNull(validator, "validator must not be null.");
  }

  @Override
  public PricingStage stage() {
    return PricingStage.CREDIT;
  }

  @Override
  public CalculationResult apply(PricingContext context, CalculationResult result) {
    Validate.notNull(context, "context must not be null.");
    Validate.notNull(result, "result must not be null.");
    List<CreditResult> creditResults = policy.apply(result.prices(), context.lines(), context);
    List<Price> prices = creditResults.stream().map(CreditResult::price).toList();
    List<CalculationIssue> issues = new ArrayList<>();
    for (CreditResult creditResult : creditResults) {
      issues.addAll(validator.validate(creditResult, context));
    }
    return result.withPrices(prices).addIssues(issues);
  }
}
