package io.genfin.pricing.internal.coupon;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.coupon.CouponCode;
import io.genfin.pricing.port.coupon.CouponStrategy;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.util.List;

/**
 * A {@link CouponStrategy} that reads the single {@link CouponCode} a customer presented from
 * {@link io.genfin.pricing.pricing.PricingAttributes}, under a configurable attribute key. Backs
 * {@link io.genfin.pricing.coupon.CouponStrategies#submitted()}.
 */
public final class SubmittedCodeCouponStrategy implements CouponStrategy {

  private final String attributeKey;

  public SubmittedCodeCouponStrategy(String attributeKey) {
    this.attributeKey = Validate.notBlank(attributeKey, "attributeKey must not be blank.");
  }

  @Override
  public boolean supports(PricingRequest.Line line, PricingContext context) {
    Validate.notNull(line, "line must not be null.");
    Validate.notNull(context, "context must not be null.");
    return context.attributes().find(attributeKey).isPresent();
  }

  @Override
  public List<CouponCode> resolve(PricingRequest.Line line, PricingContext context) {
    Validate.notNull(line, "line must not be null.");
    Validate.notNull(context, "context must not be null.");
    return context
        .attributes()
        .find(attributeKey)
        .map(value -> List.of(CouponCode.of(value)))
        .orElse(List.of());
  }
}
