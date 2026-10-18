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
 * A {@link Price}'s total {@link PriceType#PROMOTION} contribution must be a reduction (zero or
 * negative) - a promotion can never raise the price it was applied to. Mirrors {@link
 * CouponValidationRule}.
 */
public final class PromotionValidationRule implements ValidationRule {

  @Override
  public List<CalculationIssue> apply(CalculationResult result, ValidationContext context) {
    List<CalculationIssue> issues = new ArrayList<>();
    for (Price price : result.prices()) {
      Money promotion = price.breakdown().amountOf(PriceType.PROMOTION);
      if (promotion.isPositive()) {
        issues.add(
            CalculationIssue.of(
                "PROMOTION_INCREASES_PRICE",
                "promotion amount for catalog item " + price.catalogId().value() + " is positive.",
                Severity.ERROR));
      }
    }
    return issues;
  }
}
