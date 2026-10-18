package io.genfin.money.port.precision;

import io.genfin.money.currency.Currency;

/** Resolves the final decimal scale a {@code Money} amount is normalized to, per currency. */
public interface ScalePolicy {

  int scaleFor(Currency currency);
}
