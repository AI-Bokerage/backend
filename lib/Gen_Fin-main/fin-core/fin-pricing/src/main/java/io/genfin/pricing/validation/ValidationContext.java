package io.genfin.pricing.validation;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.util.CollectionUtils;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.rule.RuleResult;
import java.util.List;

/**
 * Everything a {@link ValidationRule} may need beyond the {@link
 * io.genfin.pricing.calculation.CalculationResult} itself. Only {@code pricingContext} is
 * mandatory; {@code minPrice} is {@code null} and {@code ruleResults} is empty unless the caller
 * enriches them - a rule that needs a field it does not find simply reports no issue. Mirrors
 * {@code io.genfin.ledger.validation.ValidationContext}.
 */
public record ValidationContext(
    PricingContext pricingContext, Money minPrice, List<RuleResult> ruleResults)
    implements ValueObject {

  public ValidationContext {
    Validate.notNull(pricingContext, "pricingContext must not be null.");
    ruleResults = CollectionUtils.immutableList(ruleResults);
  }

  public static ValidationContext of(PricingContext pricingContext) {
    return new ValidationContext(pricingContext, null, List.of());
  }

  /**
   * Carries the configured minimum price (e.g. from {@code
   * io.genfin.pricing.config.CommercialRuleConfiguration}'s {@code RuleContext}) for {@link
   * io.genfin.pricing.internal.validation.rules.ConfigurationValidationRule} to check every priced
   * line against.
   */
  public ValidationContext withMinPrice(Money minPrice) {
    return new ValidationContext(pricingContext, minPrice, ruleResults);
  }

  /**
   * Carries the {@link RuleResult}s an already-run {@code
   * io.genfin.pricing.port.rule.CommercialRuleEngine} produced, so the Pricing Validation stage can
   * fold them into the same collected list of issues as every other validation rule.
   */
  public ValidationContext withRuleResults(List<RuleResult> ruleResults) {
    return new ValidationContext(pricingContext, minPrice, ruleResults);
  }
}
