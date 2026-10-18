package io.genfin.money.currency;

import java.util.Locale;

/**
 * Builds {@link Currency} instances. Consumers must not call the {@code Currency} constructor
 * directly.
 */
public final class CurrencyFactory {

  private String code;
  private Integer numericCode;
  private String symbol;
  private String displayName;
  private int fractionDigits = 2;
  private Locale locale;
  private boolean active = true;

  private CurrencyFactory() {}

  public static CurrencyFactory newCurrency() {
    return new CurrencyFactory();
  }

  public CurrencyFactory code(String code) {
    this.code = code;
    return this;
  }

  public CurrencyFactory numericCode(int numericCode) {
    this.numericCode = numericCode;
    return this;
  }

  public CurrencyFactory symbol(String symbol) {
    this.symbol = symbol;
    return this;
  }

  public CurrencyFactory displayName(String displayName) {
    this.displayName = displayName;
    return this;
  }

  public CurrencyFactory fractionDigits(int fractionDigits) {
    this.fractionDigits = fractionDigits;
    return this;
  }

  public CurrencyFactory locale(Locale locale) {
    this.locale = locale;
    return this;
  }

  public CurrencyFactory active(boolean active) {
    this.active = active;
    return this;
  }

  public Currency build() {
    return new Currency(code, numericCode, symbol, displayName, fractionDigits, locale, active);
  }
}
