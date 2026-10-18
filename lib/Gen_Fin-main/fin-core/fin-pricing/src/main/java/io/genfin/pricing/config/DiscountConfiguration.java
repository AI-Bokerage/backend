package io.genfin.pricing.config;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.port.discount.DiscountPolicy;

/** The Discount Engine policy set for a fin-pricing deployment. */
public final class DiscountConfiguration {

  private final DiscountPolicy discountPolicy;

  private DiscountConfiguration(Builder builder) {
    this.discountPolicy =
        Validate.notNull(builder.discountPolicy, "discountPolicy must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public DiscountPolicy discountPolicy() {
    return discountPolicy;
  }

  public static final class Builder {

    private DiscountPolicy discountPolicy;

    public Builder discountPolicy(DiscountPolicy discountPolicy) {
      this.discountPolicy = discountPolicy;
      return this;
    }

    public DiscountConfiguration build() {
      return new DiscountConfiguration(this);
    }
  }
}
