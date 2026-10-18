package io.genfin.tax.api.exception;

import io.genfin.api.exception.GenFinException;
import java.util.Map;

/**
 * Thrown when no configured {@code TaxRate} exists for a component the resolved treatment requires.
 */
public final class UnsupportedTransactionException extends GenFinException {

  public UnsupportedTransactionException(String detail) {
    super(TaxErrorCode.UNSUPPORTED_TRANSACTION, detail, null, Map.of());
  }
}
