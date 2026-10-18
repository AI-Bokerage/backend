package io.genfin.money.conversion;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.currency.Currency;
import java.math.BigDecimal;
import java.time.Instant;

/** A point-in-time rate for converting one unit of {@code base} into {@code quote}. */
public record ExchangeRate(Currency base, Currency quote, BigDecimal rate, Instant asOf)
    implements ValueObject {

  public ExchangeRate {
    Validate.notNull(base, "base must not be null.");
    Validate.notNull(quote, "quote must not be null.");
    Validate.notNull(rate, "rate must not be null.");
    Validate.positive(rate.signum(), "rate must be positive.");
    Validate.notNull(asOf, "asOf must not be null.");
  }

  public ExchangeRate invert() {
    return new ExchangeRate(
        quote, base, BigDecimal.ONE.divide(rate, java.math.MathContext.DECIMAL64), asOf);
  }
}
