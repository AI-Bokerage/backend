package io.genfin.pricing.internal.strategy;

import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.pricing.port.strategy.PricingConflictStrategy;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.strategy.PricingCandidate;
import java.util.Comparator;
import java.util.List;

/**
 * Picks the single candidate that leaves the customer with the lowest resulting price ({@link
 * PricingCandidate#resultingAmount(Money)} against {@code runningAmount}) - unlike {@link
 * HighestDiscountStrategy}, which compares reduction size in isolation, this compares the actual
 * final amount, so it stays correct even when candidates apply against different bases.
 */
public final class BestPriceStrategy implements PricingConflictStrategy {

  @Override
  public List<PricingCandidate> select(
      List<PricingCandidate> candidates, Money runningAmount, PricingContext context) {
    Validate.notNull(candidates, "candidates must not be null.");
    Validate.notNull(runningAmount, "runningAmount must not be null.");
    return candidates.stream()
        .min(Comparator.comparing(candidate -> candidate.resultingAmount(runningAmount)))
        .map(List::of)
        .orElseGet(List::of);
  }
}
