package io.genfin.pricing.discount;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * Pairs a {@link Discount} with the {@link DiscountCondition} that decides whether it applies to a
 * given line - an application's declarative discount catalog entry (e.g. {@code "SUMMER10"}),
 * consumed by {@link io.genfin.pricing.internal.discount.RuleBasedDiscountStrategy} (see {@link
 * DiscountStrategies#fromRules}) rather than a bespoke {@code
 * io.genfin.pricing.port.discount.DiscountStrategy} per rule.
 */
public record DiscountRule(String code, Discount discount, DiscountCondition condition)
    implements ValueObject {

  public DiscountRule {
    Validate.notBlank(code, "code must not be blank.");
    Validate.notNull(discount, "discount must not be null.");
    Validate.notNull(condition, "condition must not be null.");
  }

  public static DiscountRule of(String code, Discount discount, DiscountCondition condition) {
    return new DiscountRule(code, discount, condition);
  }

  /** A rule that always applies its {@link Discount}, regardless of context. */
  public static DiscountRule always(String code, Discount discount) {
    return new DiscountRule(code, discount, (line, context) -> true);
  }
}
