package io.genfin.pricing.internal.strategy;

import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.pricing.port.strategy.PricingConflictStrategy;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.strategy.PricingCandidate;
import java.util.List;

/**
 * Keeps every eligible candidate rather than picking a single winner, maximizing the customer's
 * total savings by letting them all stack - the opposite policy to every other {@link
 * PricingConflictStrategy} here, which each pick exactly one winner.
 */
public final class MaximumSavingsStrategy implements PricingConflictStrategy {

  @Override
  public List<PricingCandidate> select(
      List<PricingCandidate> candidates, Money runningAmount, PricingContext context) {
    Validate.notNull(candidates, "candidates must not be null.");
    return List.copyOf(candidates);
  }
}
