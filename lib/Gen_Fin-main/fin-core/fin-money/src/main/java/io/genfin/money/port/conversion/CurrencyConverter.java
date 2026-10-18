package io.genfin.money.port.conversion;

import io.genfin.api.port.spi.Extension;
import io.genfin.money.conversion.ConversionContext;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;

/**
 * Converts a {@link Money} amount from its currency into another, under an explicit {@link
 * ConversionContext}.
 */
public interface CurrencyConverter extends Extension {

  Money convert(Money source, Currency target, ConversionContext context);
}
