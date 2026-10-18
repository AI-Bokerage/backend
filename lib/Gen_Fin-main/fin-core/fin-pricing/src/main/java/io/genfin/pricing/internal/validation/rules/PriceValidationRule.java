package io.genfin.pricing.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.pricing.calculation.CalculationIssue;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.validation.ValidationContext;
import io.genfin.pricing.validation.ValidationRule;
import java.util.ArrayList;
import java.util.List;

/**
 * Every resolved {@link Price}'s net amount must not be negative - however many discounts,
 * promotions, coupons or credits reduced it, a priced line can never owe a negative amount.
 */
public final class PriceValidationRule implements ValidationRule {

  @Override
  public List<CalculationIssue> apply(CalculationResult result, ValidationContext context) {
    List<CalculationIssue> issues = new ArrayList<>();
    for (Price price : result.prices()) {
      if (price.amount().isNegative()) {
        issues.add(
            CalculationIssue.of(
                "NEGATIVE_NET_PRICE",
                "price for catalog item " + price.catalogId().value() + " resolved negative.",
                Severity.CRITICAL));
      }
    }
    return issues;
  }
}
