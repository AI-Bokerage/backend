package io.genfin.money.internal.conversion;

import io.genfin.money.arithmetic.ArithmeticPolicies;
import io.genfin.money.conversion.ConversionContext;
import io.genfin.money.conversion.ExchangeRate;
import io.genfin.money.currency.Currency;
import io.genfin.money.exception.UnsupportedConversionException;
import io.genfin.money.money.Money;
import io.genfin.money.port.conversion.CurrencyConverter;
import io.genfin.money.port.conversion.ExchangeRateProvider;
import io.genfin.money.port.rounding.RoundingStrategy;
import java.math.BigDecimal;
import java.math.MathContext;

public final class DefaultCurrencyConverter implements CurrencyConverter {

  private final ExchangeRateProvider rateProvider;

  public DefaultCurrencyConverter(ExchangeRateProvider rateProvider) {
    this.rateProvider = rateProvider;
  }

  @Override
  public Money convert(Money source, Currency target, ConversionContext context) {
    if (source.currency().equals(target)) {
      return source;
    }
    ExchangeRate rate =
        rateProvider
            .rate(source.currency(), target, context)
            .orElseThrow(
                () -> new UnsupportedConversionException(source.currency().code(), target.code()));
    BigDecimal converted = source.amount().multiply(rate.rate(), MathContext.DECIMAL64);
    RoundingStrategy rounding =
        context.roundingOverride() != null
            ? context.roundingOverride()
            : ArithmeticPolicies.standard().roundingStrategy();
    return Money.of(rounding.round(converted, target.fractionDigits()), target);
  }
}
