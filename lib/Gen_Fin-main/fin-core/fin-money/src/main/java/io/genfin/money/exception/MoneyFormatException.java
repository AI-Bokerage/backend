package io.genfin.money.exception;

public class MoneyFormatException extends io.genfin.api.exception.GenFinException {

  public MoneyFormatException(String text, Throwable cause) {
    super(MoneyErrorCode.INVALID_MONEY_FORMAT, "Could not parse as Money: '" + text + "'", cause);
  }
}
