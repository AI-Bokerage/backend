package io.genfin.pricing.internal.strategy;

import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.pricing.port.strategy.PricingConflictStrategy;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.strategy.PricingCandidate;
import java.util.Comparator;
import java.util.List;

/**
 * Picks the single candidate with the highest declared {@link PricingCandidate#priority()},
 * regardless of reduction size - ties keep whichever candidate was offered first. fin-pricing's
 * default conflict-resolution strategy, matching how {@code
 * io.genfin.pricing.promotion.PromotionCalculator} historically picked a winning campaign.
 */
public final class PriorityOrderStrategy implements PricingConflictStrategy {

  @Override
  public List<PricingCandidate> select(
      List<PricingCandidate> candidates, Money runningAmount, PricingContext context) {
    Validate.notNull(candidates, "candidates must not be null.");
    return candidates.stream()
        .max(Comparator.comparingInt(PricingCandidate::priority))
        .map(List::of)
        .orElseGet(List::of);
  }
}
