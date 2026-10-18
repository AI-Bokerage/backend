package io.genfin.pricing.internal.strategy;

import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.pricing.port.strategy.PricingConflictStrategy;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.strategy.PricingCandidate;
import java.util.List;

/**
 * Enforces that at most one promotion ever applies to a line, by delegating winner selection to
 * {@link PriorityOrderStrategy} - a distinct, explicitly-named strategy so an application
 * configuring "promotions never stack" can select it by intent rather than reaching for {@link
 * PriorityOrderStrategy} and relying on incidental behavior.
 */
public final class ExclusivePromotionStrategy implements PricingConflictStrategy {

  private final PricingConflictStrategy delegate = new PriorityOrderStrategy();

  @Override
  public List<PricingCandidate> select(
      List<PricingCandidate> candidates, Money runningAmount, PricingContext context) {
    Validate.notNull(candidates, "candidates must not be null.");
    return delegate.select(candidates, runningAmount, context);
  }
}
