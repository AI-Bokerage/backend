package io.genfin.pricing.coupon;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.pricing.internal.coupon.DefaultCouponValidator;
import io.genfin.pricing.port.coupon.CouponRegistry;

/**
 * Factory for {@link CouponValidator} instances. Mirrors {@code
 * io.genfin.pricing.discount.DiscountValidators}.
 */
public final class CouponValidators {

  private CouponValidators() {}

  /** The default structural checks: usage-limit exhaustion and a non-negative net amount. */
  public static CouponValidator standard(CouponRegistry registry) {
    return new DefaultCouponValidator(registry);
  }

  /** Resolves the {@link CouponValidator} registered in {@code registry}, or {@link #standard}. */
  public static CouponValidator from(ExtensionRegistry registry, CouponRegistry couponRegistry) {
    return registry.find(CouponValidator.class).orElseGet(() -> standard(couponRegistry));
  }
}
