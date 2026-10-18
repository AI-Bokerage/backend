package io.genfin.pricing.strategy;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.pricing.internal.strategy.BestPriceStrategy;
import io.genfin.pricing.internal.strategy.ExclusivePromotionStrategy;
import io.genfin.pricing.internal.strategy.FirstApplicableStrategy;
import io.genfin.pricing.internal.strategy.HighestDiscountStrategy;
import io.genfin.pricing.internal.strategy.MaximumSavingsStrategy;
import io.genfin.pricing.internal.strategy.PriorityOrderStrategy;
import io.genfin.pricing.port.strategy.PricingConflictStrategy;

/**
 * Factory for fin-pricing's shipped {@link PricingConflictStrategy} implementations - how the
 * Discount/Promotion/Coupon Engines resolve conflicts when more than one candidate is eligible for
 * the same line simultaneously. {@link #priorityOrder()} is the default (see {@link #from}).
 * Mirrors {@code io.genfin.pricing.discount.DiscountPolicies}.
 */
public final class PricingStrategies {

  private PricingStrategies() {}

  /** Picks the single candidate with the deepest reduction, regardless of priority. */
  public static PricingConflictStrategy highestDiscount() {
    return new HighestDiscountStrategy();
  }

  /** Picks the single candidate that leaves the lowest resulting price. */
  public static PricingConflictStrategy bestPrice() {
    return new BestPriceStrategy();
  }

  /** Picks whichever candidate was offered first, with no comparison. */
  public static PricingConflictStrategy firstApplicable() {
    return new FirstApplicableStrategy();
  }

  /** Picks the single candidate with the highest declared priority. fin-pricing's default. */
  public static PricingConflictStrategy priorityOrder() {
    return new PriorityOrderStrategy();
  }

  /** Keeps every eligible candidate, letting them all stack. */
  public static PricingConflictStrategy maximumSavings() {
    return new MaximumSavingsStrategy();
  }

  /** Enforces at most one winning promotion, by priority. */
  public static PricingConflictStrategy exclusivePromotion() {
    return new ExclusivePromotionStrategy();
  }

  /**
   * Resolves the {@link PricingConflictStrategy} registered in {@code registry} if present;
   * otherwise falls back to {@link #priorityOrder()}.
   */
  public static PricingConflictStrategy from(ExtensionRegistry registry) {
    return registry.find(PricingConflictStrategy.class).orElseGet(PricingStrategies::priorityOrder);
  }
}
