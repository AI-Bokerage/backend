package io.genfin.tax.api.party;

import io.genfin.api.validation.Validate;

/**
 * An open, jurisdiction-defined state/province code (e.g. an Indian GST state code). No fixed
 * catalog is provided — the engine only ever compares two {@link StateCode}s for equality (same
 * state vs. different state), never interprets what a specific code means.
 */
public record StateCode(String code) {

  public StateCode {
    Validate.notBlank(code, "code must not be blank.");
  }

  public static StateCode of(String code) {
    return new StateCode(code);
  }
}
