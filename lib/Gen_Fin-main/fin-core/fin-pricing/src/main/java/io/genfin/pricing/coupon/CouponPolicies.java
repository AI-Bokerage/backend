package io.genfin.pricing.coupon;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.pricing.internal.coupon.DefaultCouponPolicy;
import io.genfin.pricing.port.coupon.CouponPolicy;
import io.genfin.pricing.port.coupon.CouponRegistry;
import io.genfin.pricing.port.coupon.CouponStrategy;
import java.util.List;

/**
 * Factory for {@link CouponPolicy} instances. {@link #empty()} is a legitimate default - redeeming
 * a coupon is always optional, so a run with no {@link CouponStrategy} registered simply applies no
 * coupon. Mirrors {@code io.genfin.pricing.promotion.PromotionPolicies}.
 */
public final class CouponPolicies {

  private CouponPolicies() {}

  /** A policy carrying no strategies - every line passes through with no coupon applied. */
  public static CouponPolicy empty() {
    return new DefaultCouponPolicy(CouponRegistries.empty(), List.of());
  }

  /** A policy that resolves candidate codes from every one of {@code strategies}. */
  public static CouponPolicy of(CouponRegistry registry, List<CouponStrategy> strategies) {
    return new DefaultCouponPolicy(registry, strategies);
  }

  /**
   * Resolves the {@link CouponPolicy} registered in {@code registry} if present; otherwise builds
   * one from every registered {@link CouponStrategy}, backed by {@code couponRegistry}.
   */
  public static CouponPolicy from(ExtensionRegistry registry, CouponRegistry couponRegistry) {
    return registry
        .find(CouponPolicy.class)
        .orElseGet(() -> of(couponRegistry, registry.findAll(CouponStrategy.class)));
  }
}
