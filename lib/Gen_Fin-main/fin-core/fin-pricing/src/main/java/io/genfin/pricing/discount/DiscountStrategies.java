package io.genfin.pricing.discount;

import io.genfin.pricing.internal.discount.RuleBasedDiscountStrategy;
import io.genfin.pricing.port.discount.DiscountStrategy;
import java.util.List;

/** Factory for {@link DiscountStrategy} instances. */
public final class DiscountStrategies {

  private DiscountStrategies() {}

  /** A strategy backed by a flat list of {@link DiscountRule}s. */
  public static DiscountStrategy fromRules(List<DiscountRule> rules) {
    return new RuleBasedDiscountStrategy(rules);
  }
}
