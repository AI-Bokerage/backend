package io.genfin.tax.api.exception;

import io.genfin.api.exception.GenFinException;
import java.util.Map;

/**
 * Thrown by a resolver that (unlike the default) refuses to silently fall back to {@code NO_TAX}
 * for an unrecognised jurisdiction pairing. The default resolver never throws this — it returns
 * {@code StandardTaxTreatment.NO_TAX} instead, per V1's documented scope.
 */
public final class UnsupportedJurisdictionException extends GenFinException {

  public UnsupportedJurisdictionException(String sellerCountry, String buyerCountry) {
    super(
        TaxErrorCode.UNSUPPORTED_JURISDICTION,
        "No tax rule for seller country '"
            + sellerCountry
            + "' and buyer country '"
            + buyerCountry
            + "'.",
        null,
        Map.of("sellerCountry", sellerCountry, "buyerCountry", buyerCountry));
  }
}
