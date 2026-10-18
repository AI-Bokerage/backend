package io.genfin.money.exception;

public class DuplicateCurrencyException extends io.genfin.api.exception.GenFinException {

  public DuplicateCurrencyException(String code) {
    super(MoneyErrorCode.DUPLICATE_CURRENCY, "Currency already registered: " + code);
  }
}
