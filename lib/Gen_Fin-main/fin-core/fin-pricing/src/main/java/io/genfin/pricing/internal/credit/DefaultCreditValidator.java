package io.genfin.pricing.internal.credit;

import io.genfin.api.exception.Severity;
import io.genfin.api.validation.Validate;
import io.genfin.pricing.calculation.CalculationIssue;
import io.genfin.pricing.credit.CreditResult;
import io.genfin.pricing.credit.CreditUsage;
import io.genfin.pricing.credit.CreditValidator;
import io.genfin.pricing.port.credit.CreditRegistry;
import io.genfin.pricing.pricing.PricingContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Structural sanity checks over a {@link CreditResult}: a drawn wallet must not have already
 * exhausted its {@link CreditUsage} limit, and applying it must not drive the line's net amount
 * negative. Mirrors {@code io.genfin.pricing.internal.coupon.DefaultCouponValidator}.
 */
public final class DefaultCreditValidator implements CreditValidator {

  private final CreditRegistry registry;

  public DefaultCreditValidator(CreditRegistry registry) {
    this.registry = Validate.notNull(registry, "registry must not be null.");
  }

  @Override
  public List<CalculationIssue> validate(CreditResult result, PricingContext context) {
    Validate.notNull(result, "result must not be null.");
    Validate.notNull(context, "context must not be null.");
    List<CalculationIssue> issues = new ArrayList<>();
    if (result.price().amount().isNegative()) {
      issues.add(
          CalculationIssue.of(
              "credit-negative-net",
              "Credit applied to catalog item "
                  + result.price().catalogId().value()
                  + " drove its net amount negative.",
              Severity.ERROR));
    }
    result
        .allocation()
        .ifPresent(
            allocation -> {
              CreditUsage usage = registry.usageOf(allocation.walletId());
              if (usage.isExhausted()) {
                issues.add(
                    CalculationIssue.of(
                        "credit-wallet-exhausted",
                        "Credit wallet "
                            + allocation.walletId().value()
                            + " has no remaining balance.",
                        Severity.ERROR));
              }
            });
    return issues;
  }
}
