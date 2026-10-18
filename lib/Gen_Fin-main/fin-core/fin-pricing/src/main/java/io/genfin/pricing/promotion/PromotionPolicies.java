package io.genfin.pricing.promotion;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.pricing.internal.promotion.DefaultPromotionPolicy;
import io.genfin.pricing.port.promotion.PromotionPolicy;
import io.genfin.pricing.port.promotion.PromotionStrategy;
import io.genfin.pricing.port.strategy.PricingConflictStrategy;
import io.genfin.pricing.strategy.PricingStrategies;
import java.util.List;

/**
 * Factory for {@link PromotionPolicy} instances. {@link #empty()} is a legitimate default -
 * promoting is always optional, so a run with no {@link PromotionStrategy} registered simply
 * applies no promotions. Mirrors {@code io.genfin.pricing.discount.DiscountPolicies}.
 */
public final class PromotionPolicies {

  private PromotionPolicies() {}

  /** A policy carrying no strategies - every line passes through with no promotion applied. */
  public static PromotionPolicy empty() {
    return new DefaultPromotionPolicy(List.of());
  }

  /**
   * A policy that gathers candidates from every one of {@code strategies}, resolving conflicts via
   * {@link PricingStrategies#priorityOrder()}.
   */
  public static PromotionPolicy of(List<PromotionStrategy> strategies) {
    return new DefaultPromotionPolicy(strategies);
  }

  /** A policy that gathers candidates from every one of {@code strategies}. */
  public static PromotionPolicy of(
      List<PromotionStrategy> strategies, PricingConflictStrategy conflictStrategy) {
    return new DefaultPromotionPolicy(strategies, conflictStrategy);
  }

  /**
   * Resolves the {@link PromotionPolicy} registered in {@code registry} if present; otherwise
   * builds one from every registered {@link PromotionStrategy} and the registered {@link
   * PricingConflictStrategy} (see {@link PricingStrategies#from}).
   */
  public static PromotionPolicy from(ExtensionRegistry registry) {
    return registry
        .find(PromotionPolicy.class)
        .orElseGet(
            () -> of(registry.findAll(PromotionStrategy.class), PricingStrategies.from(registry)));
  }
}
