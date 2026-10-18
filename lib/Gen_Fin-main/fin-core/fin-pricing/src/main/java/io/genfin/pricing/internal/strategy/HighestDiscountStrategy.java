package io.genfin.pricing.internal.strategy;

import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.pricing.port.strategy.PricingConflictStrategy;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.strategy.PricingCandidate;
import java.util.Comparator;
import java.util.List;

/**
 * Picks the single candidate with the deepest reduction magnitude ({@link
 * PricingCandidate#magnitude()}), regardless of priority - ties keep whichever candidate was
 * offered first.
 */
public final class HighestDiscountStrategy implements PricingConflictStrategy {

  @Override
  public List<PricingCandidate> select(
      List<PricingCandidate> candidates, Money runningAmount, PricingContext context) {
    Validate.notNull(candidates, "candidates must not be null.");
    return candidates.stream()
        .max(Comparator.comparing(PricingCandidate::magnitude))
        .map(List::of)
        .orElseGet(List::of);
  }
}
