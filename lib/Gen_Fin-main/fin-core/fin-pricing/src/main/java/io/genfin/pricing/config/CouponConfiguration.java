package io.genfin.pricing.config;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.port.coupon.CouponPolicy;
import io.genfin.pricing.port.coupon.CouponRegistry;

/** The Coupon Engine policy set for a fin-pricing deployment. */
public final class CouponConfiguration {

  private final CouponRegistry couponRegistry;
  private final CouponPolicy couponPolicy;

  private CouponConfiguration(Builder builder) {
    this.couponRegistry =
        Validate.notNull(builder.couponRegistry, "couponRegistry must not be null.");
    this.couponPolicy = Validate.notNull(builder.couponPolicy, "couponPolicy must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public CouponRegistry couponRegistry() {
    return couponRegistry;
  }

  public CouponPolicy couponPolicy() {
    return couponPolicy;
  }

  public static final class Builder {

    private CouponRegistry couponRegistry;
    private CouponPolicy couponPolicy;

    public Builder couponRegistry(CouponRegistry couponRegistry) {
      this.couponRegistry = couponRegistry;
      return this;
    }

    public Builder couponPolicy(CouponPolicy couponPolicy) {
      this.couponPolicy = couponPolicy;
      return this;
    }

    public CouponConfiguration build() {
      return new CouponConfiguration(this);
    }
  }
}
