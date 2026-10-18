package io.genfin.pricing.credit;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.pricing.internal.credit.DefaultCreditPolicy;
import io.genfin.pricing.port.credit.CreditPolicy;
import io.genfin.pricing.port.credit.CreditRegistry;
import io.genfin.pricing.port.credit.CreditStrategy;
import java.util.List;

/**
 * Factory for {@link CreditPolicy} instances. {@link #empty()} is a legitimate default - drawing
 * against a wallet is always optional, so a run with no {@link CreditStrategy} registered simply
 * applies no credit. Mirrors {@code io.genfin.pricing.coupon.CouponPolicies}.
 */
public final class CreditPolicies {

  private CreditPolicies() {}

  /** A policy carrying no strategies - every line passes through with no credit applied. */
  public static CreditPolicy empty() {
    return new DefaultCreditPolicy(
        CreditRegistries.empty(), List.of(), CreditCalculators.fullBalance());
  }

  /** A policy that resolves candidate wallets from every one of {@code strategies}. */
  public static CreditPolicy of(CreditRegistry registry, List<CreditStrategy> strategies) {
    return of(registry, strategies, CreditCalculators.fullBalance());
  }

  /**
   * A policy that resolves candidate wallets from {@code strategies}, drawn via {@code calculator}.
   */
  public static CreditPolicy of(
      CreditRegistry registry, List<CreditStrategy> strategies, CreditCalculator calculator) {
    return new DefaultCreditPolicy(registry, strategies, calculator);
  }

  /**
   * Resolves the {@link CreditPolicy} registered in {@code registry} if present; otherwise builds
   * one from every registered {@link CreditStrategy} and {@link CreditCalculator} (defaulting to
   * {@link CreditCalculators#fullBalance()}), backed by {@code creditRegistry}.
   */
  public static CreditPolicy from(ExtensionRegistry registry, CreditRegistry creditRegistry) {
    return registry
        .find(CreditPolicy.class)
        .orElseGet(
            () ->
                of(
                    creditRegistry,
                    registry.findAll(CreditStrategy.class),
                    registry
                        .find(CreditCalculator.class)
                        .orElseGet(CreditCalculators::fullBalance)));
  }
}
