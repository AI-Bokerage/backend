package io.genfin.pricing.coupon;

import io.genfin.pricing.calculation.CalculationIssue;
import io.genfin.pricing.pricing.PricingContext;
import java.util.List;

/**
 * One extra check the Coupon Engine pipeline stage runs over a {@link CouponResult} - e.g. "net
 * amount is still not negative", "the redeemed code has not exceeded its {@link CouponUsage}
 * limit". Returns an empty list when nothing is wrong; the stage collects every {@link
 * CalculationIssue} rather than stopping at the first one found. A raised issue does not undo the
 * coupon's arithmetic - it surfaces the problem for Pricing Validation / the consuming application
 * to act on, mirroring {@code io.genfin.pricing.port.discount.DiscountValidator}'s "check after
 * applying" shape.
 */
public interface CouponValidator {

  List<CalculationIssue> validate(CouponResult result, PricingContext context);
}
