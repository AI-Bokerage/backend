package io.genfin.pricing.internal.rule;

import io.genfin.api.exception.Severity;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.port.rule.CommercialRule;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.price.PriceType;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.rule.RuleContext;
import io.genfin.pricing.rule.RuleResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Illustrative example rule: a resolved {@link Price} must not carry more than {@code
 * context.maxStackedCoupons()} coupon components. Not applicable unless the caller supplied a
 * limit.
 */
public final class CouponStackingLimitRule implements CommercialRule {

  public static final String CODE = "COUPON_STACK_LIMIT";

  @Override
  public List<RuleResult> evaluate(
      CalculationResult result, PricingContext context, RuleContext ruleContext) {
    if (ruleContext.maxStackedCoupons().isEmpty()) {
      return List.of();
    }
    int limit = ruleContext.maxStackedCoupons().get();
    List<RuleResult> results = new ArrayList<>();
    for (Price price : result.prices()) {
      int stacked = price.breakdown().componentsOf(PriceType.COUPON).size();
      if (stacked > limit) {
        results.add(
            RuleResult.of(
                CODE,
                "Catalog item "
                    + price.catalogId().value()
                    + " has "
                    + stacked
                    + " coupons stacked, exceeding the limit of "
                    + limit
                    + ".",
                Severity.ERROR));
      }
    }
    return List.copyOf(results);
  }
}
