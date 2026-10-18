package io.genfin.pricing.calculation;

import io.genfin.api.validation.Validate;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.price.PriceType;
import io.genfin.pricing.pricing.PricingSummary;
import java.util.List;

/**
 * Pure arithmetic over a set of resolved {@link Price}s - rolling every line's {@link
 * io.genfin.pricing.price.PriceBreakdown} up into one {@link PricingSummary}. Plain summation has
 * exactly one correct implementation, so unlike the SPI ports in {@code
 * io.genfin.pricing.port.calculation}, this is a static utility rather than an extension point,
 * mirroring {@code io.genfin.ledger.posting.PostingCalculator}.
 */
public final class PricingCalculator {

  private PricingCalculator() {}

  /** The summed {@link PriceType#BASE} contribution across every {@code prices}. */
  public static Money baseAmount(List<Price> prices, Currency currency) {
    return amountOf(prices, currency, PriceType.BASE);
  }

  /** The summed discount/promotion/coupon/credit reduction across every {@code prices}. */
  public static Money reductionAmount(List<Price> prices, Currency currency) {
    Money reduction = Money.zero(currency);
    for (PriceType type :
        List.of(PriceType.DISCOUNT, PriceType.PROMOTION, PriceType.COUPON, PriceType.CREDIT)) {
      reduction = reduction.add(amountOf(prices, currency, type));
    }
    return reduction;
  }

  /** The net amount across every {@code prices} - what the request resolves to overall. */
  public static Money netAmount(List<Price> prices, Currency currency) {
    Validate.notNull(prices, "prices must not be null.");
    Money net = Money.zero(currency);
    for (Price price : prices) {
      net = net.add(price.amount());
    }
    return net;
  }

  /** Rolls {@code prices} up into one coarse {@link PricingSummary}. */
  public static PricingSummary summarize(List<Price> prices, Currency currency) {
    return new PricingSummary(
        baseAmount(prices, currency),
        reductionAmount(prices, currency),
        netAmount(prices, currency));
  }

  private static Money amountOf(List<Price> prices, Currency currency, PriceType type) {
    Validate.notNull(prices, "prices must not be null.");
    Money total = Money.zero(currency);
    for (Price price : prices) {
      total = total.add(price.breakdown().amountOf(type));
    }
    return total;
  }
}
