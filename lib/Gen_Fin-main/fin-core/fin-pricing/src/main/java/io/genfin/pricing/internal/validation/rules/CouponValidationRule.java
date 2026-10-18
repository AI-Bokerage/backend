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
 * A {@link Price}'s total {@link PriceType#COUPON} contribution must be a reduction (zero or
 * negative) - a redeemed coupon can never raise the price it was applied to.
 */
public final class CouponValidationRule implements ValidationRule {

  @Override
  public List<CalculationIssue> apply(CalculationResult result, ValidationContext context) {
    List<CalculationIssue> issues = new ArrayList<>();
    for (Price price : result.prices()) {
      Money coupon = price.breakdown().amountOf(PriceType.COUPON);
      if (coupon.isPositive()) {
        issues.add(
            CalculationIssue.of(
                "COUPON_INCREASES_PRICE",
                "coupon amount for catalog item " + price.catalogId().value() + " is positive.",
                Severity.ERROR));
      }
    }
    return issues;
  }
}
