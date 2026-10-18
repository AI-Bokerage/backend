package io.genfin.pricing.internal.discount;

import io.genfin.api.exception.Severity;
import io.genfin.api.validation.Validate;
import io.genfin.pricing.calculation.CalculationIssue;
import io.genfin.pricing.port.discount.DiscountValidator;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.pricing.PricingContext;
import java.util.List;

/**
 * Structural sanity check: a line's net amount must never go negative once discounts are applied.
 * Mirrors {@code io.genfin.ledger.internal.validation.rules.BalancedPostingRule}'s single-purpose
 * shape; the detailed Commercial Rule Engine ({@code io.genfin.pricing.rule}) arrives in a later
 * stage.
 */
public final class DefaultDiscountValidator implements DiscountValidator {

  @Override
  public List<CalculationIssue> validate(Price price, PricingContext context) {
    Validate.notNull(price, "price must not be null.");
    Validate.notNull(context, "context must not be null.");
    if (price.amount().isNegative()) {
      return List.of(
          CalculationIssue.of(
              "discount-negative-net",
              "Discount(s) applied to catalog item "
                  + price.catalogId().value()
                  + " drove its net amount negative.",
              Severity.ERROR));
    }
    return List.of();
  }
}
