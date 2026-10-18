package io.genfin.money.exception;

public class UnknownCurrencyException extends io.genfin.api.exception.GenFinException {

  public UnknownCurrencyException(String code) {
    super(MoneyErrorCode.UNKNOWN_CURRENCY, "Unknown currency code: " + code);
  }
}
