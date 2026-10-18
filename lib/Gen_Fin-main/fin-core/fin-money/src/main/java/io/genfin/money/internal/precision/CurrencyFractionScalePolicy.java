package io.genfin.money.internal.precision;

import io.genfin.money.currency.Currency;
import io.genfin.money.port.precision.ScalePolicy;

/** Scales to the currency's own declared fraction digits (e.g. 2 for USD, 0 for JPY). */
public final class CurrencyFractionScalePolicy implements ScalePolicy {

  @Override
  public int scaleFor(Currency currency) {
    return currency.fractionDigits();
  }
}
