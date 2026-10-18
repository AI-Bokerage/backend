package io.genfin.pricing.discount;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.pricing.internal.discount.DefaultDiscountPolicy;
import io.genfin.pricing.port.discount.DiscountPolicy;
import io.genfin.pricing.port.discount.DiscountStrategy;
import java.util.List;

/**
 * Factory for {@link DiscountPolicy} instances. Unlike {@code
 * io.genfin.pricing.calculation.PricingPolicies}, {@link #empty()} is a legitimate default:
 * discounting is always optional, so a run with no {@link DiscountStrategy} registered simply
 * applies no discounts rather than failing. Mirrors {@code
 * io.genfin.pricing.calculation.PricingPolicies}.
 */
public final class DiscountPolicies {

  private DiscountPolicies() {}

  /** A policy carrying no strategies - every line passes through with no discount applied. */
  public static DiscountPolicy empty() {
    return new DefaultDiscountPolicy(List.of());
  }

  /** A policy that tries {@code orderedStrategies} in order, applying the first that fits. */
  public static DiscountPolicy of(List<DiscountStrategy> orderedStrategies) {
    return new DefaultDiscountPolicy(orderedStrategies);
  }

  /**
   * Resolves the {@link DiscountPolicy} registered in {@code registry} if present; otherwise builds
   * one from every registered {@link DiscountStrategy}.
   */
  public static DiscountPolicy from(ExtensionRegistry registry) {
    return registry
        .find(DiscountPolicy.class)
        .orElseGet(() -> of(registry.findAll(DiscountStrategy.class)));
  }
}
