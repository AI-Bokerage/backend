package io.genfin.pricing.credit;

import io.genfin.pricing.internal.credit.DefaultCreditRegistry;
import io.genfin.pricing.port.credit.CreditRegistry;

/**
 * Factory for {@link CreditRegistry} instances. Deliberately has no standard-wallet counterpart -
 * Gen-Fin defines no built-in wallets, so every registry starts empty until an application
 * registers its own. Mirrors {@code io.genfin.pricing.coupon.CouponRegistries}.
 */
public final class CreditRegistries {

  private CreditRegistries() {}

  public static CreditRegistry empty() {
    return new DefaultCreditRegistry();
  }
}
