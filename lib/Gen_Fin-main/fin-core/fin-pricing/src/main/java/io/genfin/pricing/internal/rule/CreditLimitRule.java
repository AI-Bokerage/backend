package io.genfin.pricing.internal.rule;

import io.genfin.api.exception.Severity;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.port.rule.CommercialRule;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.price.PriceType;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.rule.RuleContext;
import io.genfin.pricing.rule.RuleResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Illustrative example rule: the total credit applied across every resolved {@link Price} must not
 * exceed {@code context.creditLimit()}. Not applicable unless the caller supplied a limit; totals
 * are kept per currency so a mixed-currency request never mixes amounts together.
 */
public final class CreditLimitRule implements CommercialRule {

  public static final String CODE = "CREDIT_LIMIT";

  @Override
  public List<RuleResult> evaluate(
      CalculationResult result, PricingContext context, RuleContext ruleContext) {
    if (ruleContext.creditLimit().isEmpty()) {
      return List.of();
    }
    Money limit = ruleContext.creditLimit().get();
    Map<Currency, Money> totalsByCurrency = new LinkedHashMap<>();
    for (Price price : result.prices()) {
      Money credit = price.breakdown().amountOf(PriceType.CREDIT).abs();
      totalsByCurrency.merge(credit.currency(), credit, Money::add);
    }
    Money total = totalsByCurrency.get(limit.currency());
    if (total != null && total.compareTo(limit) > 0) {
      return List.of(
          RuleResult.of(
              CODE,
              "Total credit applied (" + total + ") exceeds the limit of " + limit + ".",
              Severity.ERROR));
    }
    return List.of();
  }
}
