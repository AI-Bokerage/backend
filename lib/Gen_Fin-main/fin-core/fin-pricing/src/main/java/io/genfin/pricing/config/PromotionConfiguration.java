package io.genfin.pricing.config;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.port.promotion.PromotionPolicy;

/** The Promotion Engine policy set for a fin-pricing deployment. */
public final class PromotionConfiguration {

  private final PromotionPolicy promotionPolicy;

  private PromotionConfiguration(Builder builder) {
    this.promotionPolicy =
        Validate.notNull(builder.promotionPolicy, "promotionPolicy must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public PromotionPolicy promotionPolicy() {
    return promotionPolicy;
  }

  public static final class Builder {

    private PromotionPolicy promotionPolicy;

    public Builder promotionPolicy(PromotionPolicy promotionPolicy) {
      this.promotionPolicy = promotionPolicy;
      return this;
    }

    public PromotionConfiguration build() {
      return new PromotionConfiguration(this);
    }
  }
}
