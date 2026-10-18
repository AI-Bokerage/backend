package io.genfin.pricing.internal.strategy;

import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.pricing.port.strategy.PricingConflictStrategy;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.strategy.PricingCandidate;
import java.util.List;

/**
 * Picks whichever candidate was offered first, with no comparison at all - the cheapest possible
 * conflict-resolution rule, useful when candidate order already reflects the application's intended
 * precedence.
 */
public final class FirstApplicableStrategy implements PricingConflictStrategy {

  @Override
  public List<PricingCandidate> select(
      List<PricingCandidate> candidates, Money runningAmount, PricingContext context) {
    Validate.notNull(candidates, "candidates must not be null.");
    return candidates.isEmpty() ? List.of() : List.of(candidates.get(0));
  }
}
