package io.genfin.pricing.calculation;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.pricing.internal.calculation.DefaultPricingPolicy;
import io.genfin.pricing.port.calculation.PricingPolicy;
import io.genfin.pricing.port.calculation.PricingStrategy;
import java.util.List;

/**
 * Factory for {@link PricingPolicy} instances. Deliberately has no {@code standard()} default -
 * Gen-Fin defines no built-in prices for any catalog item, so a policy is only ever the strategies
 * an application explicitly supplies. Mirrors {@code io.genfin.ledger.posting.PostingPolicies}.
 */
public final class PricingPolicies {

  private PricingPolicies() {}

  /** A policy carrying no strategies at all - every {@link PricingPolicy#resolve} call fails. */
  public static PricingPolicy empty() {
    return new DefaultPricingPolicy(List.of());
  }

  /** A policy that tries {@code orderedStrategies} in order, resolving with the first that fits. */
  public static PricingPolicy of(List<PricingStrategy> orderedStrategies) {
    return new DefaultPricingPolicy(orderedStrategies);
  }

  /**
   * Resolves the {@link PricingPolicy} registered in {@code registry} if present; otherwise builds
   * one from every registered {@link PricingStrategy}.
   */
  public static PricingPolicy from(ExtensionRegistry registry) {
    return registry
        .find(PricingPolicy.class)
        .orElseGet(() -> of(registry.findAll(PricingStrategy.class)));
  }
}
