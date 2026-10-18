package io.genfin.money.currency;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A currency, independent of {@link java.util.Currency} so custom, crypto, and internal enterprise
 * currencies are first-class. Two currencies are equal when their {@link #code()} matches.
 */
public final class Currency implements ValueObject, Serializable {

  private final String code;
  private final Integer numericCode;
  private final String symbol;
  private final String displayName;
  private final int fractionDigits;
  private final Locale locale;
  private final boolean active;

  Currency(
      String code,
      Integer numericCode,
      String symbol,
      String displayName,
      int fractionDigits,
      Locale locale,
      boolean active) {
    this.code = Validate.notBlank(code, "code must not be blank.");
    this.numericCode = numericCode;
    this.symbol = Validate.notBlank(symbol, "symbol must not be blank.");
    this.displayName = Validate.notBlank(displayName, "displayName must not be blank.");
    this.fractionDigits =
        Validate.nonNegative(fractionDigits, "fractionDigits must not be negative.");
    this.locale = locale;
    this.active = active;
  }

  public String code() {
    return code;
  }

  public Optional<Integer> numericCode() {
    return Optional.ofNullable(numericCode);
  }

  public String symbol() {
    return symbol;
  }

  public String displayName() {
    return displayName;
  }

  public int fractionDigits() {
    return fractionDigits;
  }

  public Optional<Locale> locale() {
    return Optional.ofNullable(locale);
  }

  public boolean active() {
    return active;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Currency that)) {
      return false;
    }
    return code.equals(that.code);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code);
  }

  @Override
  public String toString() {
    return code;
  }
}
