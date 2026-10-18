package io.genfin.money.exception;

public class MoneyOverflowException extends io.genfin.api.exception.GenFinException {

  public MoneyOverflowException(String message) {
    super(MoneyErrorCode.ARITHMETIC_OVERFLOW, message);
  }
}
