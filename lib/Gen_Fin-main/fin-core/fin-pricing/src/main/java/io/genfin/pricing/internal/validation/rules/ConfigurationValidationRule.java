package io.genfin.pricing.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.money.money.Money;
import io.genfin.pricing.calculation.CalculationIssue;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.validation.ValidationContext;
import io.genfin.pricing.validation.ValidationRule;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks every resolved {@link Price} against the deployment's configured minimum price (see {@link
 * ValidationContext#minPrice()}, typically sourced from {@code
 * io.genfin.pricing.config.CommercialRuleConfiguration}'s {@code RuleContext}). Reports no issue
 * when the context carries no minimum price - the rule has nothing to check.
 */
public final class ConfigurationValidationRule implements ValidationRule {

  @Override
  public List<CalculationIssue> apply(CalculationResult result, ValidationContext context) {
    Money minPrice = context.minPrice();
    if (minPrice == null) {
      return List.of();
    }
    List<CalculationIssue> issues = new ArrayList<>();
    for (Price price : result.prices()) {
      if (price.amount().compareTo(minPrice) < 0) {
        issues.add(
            CalculationIssue.of(
                "PRICE_BELOW_CONFIGURED_MINIMUM",
                "price for catalog item "
                    + price.catalogId().value()
                    + " is below the configured minimum price.",
                Severity.ERROR));
      }
    }
    return issues;
  }
}
