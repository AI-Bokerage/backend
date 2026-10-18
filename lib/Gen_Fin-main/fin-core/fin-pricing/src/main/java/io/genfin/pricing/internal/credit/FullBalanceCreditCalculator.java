package io.genfin.pricing.internal.credit;

import io.genfin.money.money.Money;
import io.genfin.pricing.credit.CreditBalance;
import io.genfin.pricing.credit.CreditCalculator;
import io.genfin.pricing.price.PriceAdjustment;
import io.genfin.pricing.price.PriceModifier;
import io.genfin.pricing.price.PriceType;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;

/**
 * The standard {@link CreditCalculator}: offsets a line's running amount by the wallet's full
 * {@link CreditBalance#total()}, capped so the line is never driven negative. Backs {@link
 * io.genfin.pricing.credit.CreditCalculators#fullBalance()}.
 */
public final class FullBalanceCreditCalculator implements CreditCalculator {

  @Override
  public PriceAdjustment applyTo(
      Money runningAmount,
      CreditBalance balance,
      PricingRequest.Line line,
      PricingContext context) {
    Money drawn = balance.total().min(runningAmount);
    return PriceAdjustment.of(
        PriceType.CREDIT,
        PriceModifier.fixed(drawn.negate()),
        runningAmount,
        "Wallet credit " + balance.walletId().value());
  }
}
