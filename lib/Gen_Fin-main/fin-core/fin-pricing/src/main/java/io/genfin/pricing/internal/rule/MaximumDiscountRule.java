package io.genfin.pricing.internal.rule;

import io.genfin.api.exception.Severity;
import io.genfin.money.money.Money;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.port.rule.CommercialRule;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.price.PriceType;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.rule.RuleContext;
import io.genfin.pricing.rule.RuleResult;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Illustrative example rule: the combined discount/promotion/coupon reduction on a resolved {@link
 * Price} must not exceed {@code context.maxDiscountPercentage()} of its base component. Not
 * applicable unless the caller supplied a limit; silent on a zero base amount to avoid a divide by
 * zero.
 */
public final class MaximumDiscountRule implements CommercialRule {

  public static final String CODE = "MAX_DISCOUNT_PERCENTAGE";

  @Override
  public List<RuleResult> evaluate(
      CalculationResult result, PricingContext context, RuleContext ruleContext) {
    if (ruleContext.maxDiscountPercentage().isEmpty()) {
      return List.of();
    }
    BigDecimal maxFraction = ruleContext.maxDiscountPercentage().get().fraction();
    List<RuleResult> results = new ArrayList<>();
    for (Price price : result.prices()) {
      Money base = price.breakdown().amountOf(PriceType.BASE);
      if (base.isZero()) {
        continue;
      }
      Money reduction =
          price
              .breakdown()
              .amountOf(PriceType.DISCOUNT)
              .add(price.breakdown().amountOf(PriceType.PROMOTION))
              .add(price.breakdown().amountOf(PriceType.COUPON));
      BigDecimal ratio =
          reduction.abs().amount().divide(base.abs().amount(), MathContext.DECIMAL64);
      if (ratio.compareTo(maxFraction) > 0) {
        results.add(
            RuleResult.of(
                CODE,
                "Discount on catalog item "
                    + price.catalogId().value()
                    + " reduces the price by "
                    + ratio.movePointRight(2)
                    + "%, exceeding the maximum of "
                    + maxFraction.movePointRight(2)
                    + "%.",
                Severity.ERROR));
      }
    }
    return List.copyOf(results);
  }
}
