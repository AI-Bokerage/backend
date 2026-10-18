package io.genfin.pricing.internal.coupon;

import io.genfin.api.exception.Severity;
import io.genfin.api.validation.Validate;
import io.genfin.pricing.calculation.CalculationIssue;
import io.genfin.pricing.coupon.CouponResult;
import io.genfin.pricing.coupon.CouponUsage;
import io.genfin.pricing.coupon.CouponValidator;
import io.genfin.pricing.port.coupon.CouponRegistry;
import io.genfin.pricing.pricing.PricingContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Structural sanity checks over a {@link CouponResult}: a redeemed code must not have already
 * exhausted its {@link CouponUsage} limit, and applying it must not drive the line's net amount
 * negative. Mirrors {@code io.genfin.pricing.internal.discount.DefaultDiscountValidator}.
 */
public final class DefaultCouponValidator implements CouponValidator {

  private final CouponRegistry registry;

  public DefaultCouponValidator(CouponRegistry registry) {
    this.registry = Validate.notNull(registry, "registry must not be null.");
  }

  @Override
  public List<CalculationIssue> validate(CouponResult result, PricingContext context) {
    Validate.notNull(result, "result must not be null.");
    Validate.notNull(context, "context must not be null.");
    List<CalculationIssue> issues = new ArrayList<>();
    if (result.price().amount().isNegative()) {
      issues.add(
          CalculationIssue.of(
              "coupon-negative-net",
              "Coupon applied to catalog item "
                  + result.price().catalogId().value()
                  + " drove its net amount negative.",
              Severity.ERROR));
    }
    result
        .redemption()
        .ifPresent(
            redemption -> {
              CouponUsage usage = registry.usageOf(redemption.code());
              if (usage.isExhausted()) {
                issues.add(
                    CalculationIssue.of(
                        "coupon-exhausted",
                        "Coupon code "
                            + redemption.code().value()
                            + " has reached its redemption limit.",
                        Severity.ERROR));
              }
            });
    return issues;
  }
}
