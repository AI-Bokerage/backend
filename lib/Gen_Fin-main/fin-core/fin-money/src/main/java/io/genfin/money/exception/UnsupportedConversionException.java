package io.genfin.money.exception;

public class UnsupportedConversionException extends io.genfin.api.exception.GenFinException {

  public UnsupportedConversionException(String base, String quote) {
    super(
        MoneyErrorCode.UNSUPPORTED_CONVERSION,
        "No exchange rate available from " + base + " to " + quote);
  }
}
