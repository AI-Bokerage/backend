package io.genfin.tax.api.exception;

import io.genfin.api.exception.GenFinException;
import java.util.Map;

/**
 * Thrown when a {@code PartyTaxProfile} is internally inconsistent (e.g. GST-registered with no GST
 * number).
 */
public final class InvalidTaxProfileException extends GenFinException {

  public InvalidTaxProfileException(String detail) {
    super(TaxErrorCode.INVALID_TAX_PROFILE, detail, null, Map.of());
  }
}
