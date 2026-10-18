package io.genfin.pricing.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.money.money.Money;
import io.genfin.pricing.calculation.CalculationIssue;
import io.genfin.pricing.calculation.CalculationResult;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.price.PriceBreakdown;
import io.genfin.pricing.price.PriceType;
import io.genfin.pricing.validation.ValidationContext;
import io.genfin.pricing.validation.ValidationRule;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Price}'s total {@link PriceType#DISCOUNT} reduction must never exceed its {@link
 * PriceType#BASE} amount - a Discount Engine result that would discount more than the base price
 * itself is invalid, however many strategies contributed to it.
 */
public final class DiscountValidationRule implements ValidationRule {

  @Override
  public List<CalculationIssue> apply(CalculationResult result, ValidationContext context) {
    List<CalculationIssue> issues = new ArrayList<>();
    for (Price price : result.prices()) {
      PriceBreakdown breakdown = price.breakdown();
      Money base = breakdown.amountOf(PriceType.BASE);
      Money discount = breakdown.amountOf(PriceType.DISCOUNT);
      if (discount.abs().compareTo(base) > 0) {
        issues.add(
            CalculationIssue.of(
                "DISCOUNT_EXCEEDS_BASE_PRICE",
                "total discount for catalog item "
                    + price.catalogId().value()
                    + " exceeds its base price.",
                Severity.ERROR));
      }
    }
    return issues;
  }
}
