package io.genfin.pricing.coupon;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.util.Locale;

/**
 * The code a customer presents to redeem a {@link CouponCampaign} - e.g. {@code "SAVE10"}. Normal-
 * ized (trimmed, upper-cased) so lookups in a {@link io.genfin.pricing.port.coupon.CouponRegistry}
 * are case-insensitive by construction rather than by convention. fin-pricing never generates or
 * stores actual codes: this type is only the shape a consuming application's codes take.
 */
public record CouponCode(String value) implements ValueObject {

  /**
   * The {@link io.genfin.pricing.pricing.PricingAttributes} key a customer-presented code is
   * conventionally carried under, read by {@link CouponStrategies#submitted()}.
   */
  public static final String ATTRIBUTE_KEY = "coupon.code";

  public CouponCode {
    Validate.notBlank(value, "value must not be blank.");
    value = value.trim().toUpperCase(Locale.ROOT);
  }

  public static CouponCode of(String value) {
    return new CouponCode(value);
  }
}
