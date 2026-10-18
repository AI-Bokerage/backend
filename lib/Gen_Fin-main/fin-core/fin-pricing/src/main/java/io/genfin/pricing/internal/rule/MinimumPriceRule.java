package io.genfin.pricing.internal.rule;

import io.genfin.api.exception.Severity;
import io.genfin.money.money.Money;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.port.rule.CommercialRule;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.rule.RuleContext;
import io.genfin.pricing.rule.RuleResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Illustrative example rule: every resolved {@link Price} must be at least {@code
 * context.minPrice()}. Not applicable unless the caller supplied a minimum and the price's currency
 * matches it.
 */
public final class MinimumPriceRule implements CommercialRule {

  public static final String CODE = "MIN_PRICE";

  @Override
  public List<RuleResult> evaluate(
      CalculationResult result, PricingContext context, RuleContext ruleContext) {
    if (ruleContext.minPrice().isEmpty()) {
      return List.of();
    }
    Money minPrice = ruleContext.minPrice().get();
    List<RuleResult> results = new ArrayList<>();
    for (Price price : result.prices()) {
      if (!price.amount().currency().equals(minPrice.currency())) {
        continue;
      }
      if (price.amount().compareTo(minPrice) < 0) {
        results.add(
            RuleResult.of(
                CODE,
                "Price for catalog item "
                    + price.catalogId().value()
                    + " ("
                    + price.amount()
                    + ") is below the minimum of "
                    + minPrice
                    + ".",
                Severity.ERROR));
      }
    }
    return List.copyOf(results);
  }
}
