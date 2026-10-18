package io.genfin.tax.api.exception;

import io.genfin.api.exception.GenFinException;
import java.util.Map;

/** Thrown when a seller or buyer {@code PartyTaxProfile} was required but not supplied. */
public final class MissingTaxProfileException extends GenFinException {

  public MissingTaxProfileException(String party) {
    super(
        TaxErrorCode.MISSING_TAX_PROFILE,
        "Missing tax profile for " + party + ".",
        null,
        Map.of("party", party));
  }
}
