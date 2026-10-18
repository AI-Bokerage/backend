package io.genfin.pricing.coupon;

import io.genfin.pricing.internal.coupon.DefaultCouponRegistry;
import io.genfin.pricing.port.coupon.CouponRegistry;

/**
 * Factory for {@link CouponRegistry} instances. Deliberately has no standard-catalog counterpart -
 * Gen-Fin defines no built-in coupons, so every registry starts empty until an application
 * registers its own codes. Mirrors {@code io.genfin.pricing.catalog.CatalogRegistries}.
 */
public final class CouponRegistries {

  private CouponRegistries() {}

  public static CouponRegistry empty() {
    return new DefaultCouponRegistry();
  }
}
