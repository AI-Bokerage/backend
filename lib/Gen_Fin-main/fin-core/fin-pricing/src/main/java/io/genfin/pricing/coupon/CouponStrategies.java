package io.genfin.pricing.coupon;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.internal.coupon.SubmittedCodeCouponStrategy;
import io.genfin.pricing.port.coupon.CouponStrategy;

/**
 * Factory for {@link CouponStrategy} instances. {@link #submitted()} is the standard way a
 * customer-presented code reaches the Coupon Engine - via {@link
 * io.genfin.pricing.pricing.PricingAttributes} - which an application populates from whatever
 * checkout/cart input it collects; fin-pricing has no other opinion on where codes come from.
 */
public final class CouponStrategies {

  private CouponStrategies() {}

  /** Reads the presented {@link CouponCode} from {@link CouponCode#ATTRIBUTE_KEY}. */
  public static CouponStrategy submitted() {
    return submitted(CouponCode.ATTRIBUTE_KEY);
  }

  /** Reads the presented {@link CouponCode} from {@code attributeKey}. */
  public static CouponStrategy submitted(String attributeKey) {
    Validate.notBlank(attributeKey, "attributeKey must not be blank.");
    return new SubmittedCodeCouponStrategy(attributeKey);
  }
}
