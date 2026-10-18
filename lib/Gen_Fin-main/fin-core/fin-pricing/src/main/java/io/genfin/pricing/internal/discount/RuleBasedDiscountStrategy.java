package io.genfin.pricing.internal.discount;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.discount.Discount;
import io.genfin.pricing.discount.DiscountRule;
import io.genfin.pricing.port.discount.DiscountStrategy;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.util.List;

/**
 * A {@link DiscountStrategy} backed by a flat list of {@link DiscountRule}s: every rule whose
 * {@link DiscountRule#condition()} matches the line contributes its {@link
 * DiscountRule#discount()}. Lets an application declare its discount catalog as data rather than
 * writing a bespoke {@link DiscountStrategy} per case.
 */
public final class RuleBasedDiscountStrategy implements DiscountStrategy {

  private final List<DiscountRule> rules;

  public RuleBasedDiscountStrategy(List<DiscountRule> rules) {
    this.rules = List.copyOf(rules);
  }

  @Override
  public boolean supports(PricingRequest.Line line, PricingContext context) {
    Validate.notNull(line, "line must not be null.");
    Validate.notNull(context, "context must not be null.");
    return rules.stream().anyMatch(rule -> rule.condition().test(line, context));
  }

  @Override
  public List<Discount> resolve(PricingRequest.Line line, PricingContext context) {
    Validate.notNull(line, "line must not be null.");
    Validate.notNull(context, "context must not be null.");
    return rules.stream()
        .filter(rule -> rule.condition().test(line, context))
        .map(DiscountRule::discount)
        .toList();
  }
}
