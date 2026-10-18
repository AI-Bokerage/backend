package io.genfin.money.exception;

import io.genfin.api.exception.GenFinException;
import java.util.Map;

public class CurrencyMismatchException extends GenFinException {

  public CurrencyMismatchException(String left, String right) {
    super(
        MoneyErrorCode.CURRENCY_MISMATCH,
        "Cannot operate on Money in " + left + " and " + right + " without a conversion strategy.",
        null,
        Map.of("left", left, "right", right));
  }
}
