package io.genfin.pricing.credit;

import io.genfin.pricing.calculation.CalculationIssue;
import io.genfin.pricing.pricing.PricingContext;
import java.util.List;

/**
 * One extra check the Credit Engine pipeline stage runs over a {@link CreditResult} - e.g. "net
 * amount is still not negative", "the drawn wallet has not exhausted its {@link CreditUsage}
 * limit". Returns an empty list when nothing is wrong; the stage collects every {@link
 * CalculationIssue} rather than stopping at the first one found. A raised issue does not undo the
 * credit's arithmetic - it surfaces the problem for Pricing Validation / the consuming application
 * to act on, mirroring {@code io.genfin.pricing.coupon.CouponValidator}'s "check after applying"
 * shape.
 */
public interface CreditValidator {

  List<CalculationIssue> validate(CreditResult result, PricingContext context);
}
