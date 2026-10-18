package io.genfin.pricing.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.money.money.Money;
import io.genfin.pricing.calculation.CalculationIssue;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.price.PriceType;
import io.genfin.pricing.validation.ValidationContext;
import io.genfin.pricing.validation.ValidationRule;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Price}'s total {@link PriceType#CREDIT} contribution must be a reduction (zero or
 * negative) - drawing against a wallet can never raise the price it was applied to. Mirrors {@link
 * CouponValidationRule}.
 */
public final class CreditValidationRule implements ValidationRule {

  @Override
  public List<CalculationIssue> apply(CalculationResult result, ValidationContext context) {
    List<CalculationIssue> issues = new ArrayList<>();
    for (Price price : result.prices()) {
      Money credit = price.breakdown().amountOf(PriceType.CREDIT);
      if (credit.isPositive()) {
        issues.add(
            CalculationIssue.of(
                "CREDIT_INCREASES_PRICE",
                "credit amount for catalog item " + price.catalogId().value() + " is positive.",
                Severity.ERROR));
      }
    }
    return issues;
  }
}
