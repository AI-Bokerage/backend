package io.genfin.pricing.credit;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.pricing.internal.credit.DefaultCreditValidator;
import io.genfin.pricing.port.credit.CreditRegistry;

/**
 * Factory for {@link CreditValidator} instances. Mirrors {@code
 * io.genfin.pricing.coupon.CouponValidators}.
 */
public final class CreditValidators {

  private CreditValidators() {}

  /** The default structural checks: usage-limit exhaustion and a non-negative net amount. */
  public static CreditValidator standard(CreditRegistry registry) {
    return new DefaultCreditValidator(registry);
  }

  /** Resolves the {@link CreditValidator} registered in {@code registry}, or {@link #standard}. */
  public static CreditValidator from(ExtensionRegistry registry, CreditRegistry creditRegistry) {
    return registry.find(CreditValidator.class).orElseGet(() -> standard(creditRegistry));
  }
}
