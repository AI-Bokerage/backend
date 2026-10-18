package io.genfin.tax.api.party;

import io.genfin.api.validation.Validate;

/**
 * Open-value-type implementation of {@link CountryCode}. {@link #INDIA} is the only jurisdiction
 * Tax Engine V1 resolves rules for; every other code falls through to {@code NO_TAX}.
 */
public record StandardCountryCode(String code) implements CountryCode {

  public StandardCountryCode {
    Validate.notBlank(code, "code must not be blank.");
  }

  public static StandardCountryCode of(String code) {
    return new StandardCountryCode(code);
  }

  public static final StandardCountryCode INDIA = of("IN");
}
