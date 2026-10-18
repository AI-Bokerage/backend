package io.genfin.tax.api.party;

import io.genfin.api.validation.Validate;
import io.genfin.tax.api.exception.InvalidTaxProfileException;
import java.util.Optional;

/**
 * Everything the Tax Decision Resolver needs to know about one party (seller or buyer) in a
 * transaction — never a Tenant, Client, or Customer. The caller (e.g. FIN-SVC) is responsible for
 * adapting its own domain object into this shape.
 */
public record PartyTaxProfile(
    CountryCode country,
    StateCode state,
    RegistrationType registrationType,
    String gstNumber,
    String lutNumber) {

  public PartyTaxProfile {
    Validate.notNull(country, "country must not be null.");
    Validate.notNull(registrationType, "registrationType must not be null.");
    if (StandardRegistrationType.GST_REGISTERED.code().equals(registrationType.code())
        && (gstNumber == null || gstNumber.isBlank())) {
      throw new InvalidTaxProfileException("A GST_REGISTERED party must supply a gstNumber.");
    }
    if (StandardRegistrationType.LUT_REGISTERED.code().equals(registrationType.code())
        && (lutNumber == null || lutNumber.isBlank())) {
      throw new InvalidTaxProfileException("An LUT_REGISTERED party must supply a lutNumber.");
    }
  }

  public static PartyTaxProfile of(
      CountryCode country, StateCode state, RegistrationType registrationType) {
    return new PartyTaxProfile(country, state, registrationType, null, null);
  }

  public static PartyTaxProfile gstRegistered(
      CountryCode country, StateCode state, String gstNumber) {
    return new PartyTaxProfile(
        country, state, StandardRegistrationType.GST_REGISTERED, gstNumber, null);
  }

  public static PartyTaxProfile lutRegistered(
      CountryCode country, StateCode state, String lutNumber) {
    return new PartyTaxProfile(
        country, state, StandardRegistrationType.LUT_REGISTERED, null, lutNumber);
  }

  public static PartyTaxProfile foreign(CountryCode country) {
    return new PartyTaxProfile(country, null, StandardRegistrationType.FOREIGN_ENTITY, null, null);
  }

  public Optional<StateCode> stateOptional() {
    return Optional.ofNullable(state);
  }

  public Optional<String> gstNumberOptional() {
    return Optional.ofNullable(gstNumber);
  }

  public Optional<String> lutNumberOptional() {
    return Optional.ofNullable(lutNumber);
  }
}
