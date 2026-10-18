package io.genfin.tax.port;

import io.genfin.api.port.spi.Extension;
import io.genfin.tax.api.exception.InvalidTaxProfileException;
import io.genfin.tax.api.party.PartyTaxProfile;

/**
 * Validates that a party's registration standing is usable for tax resolution — beyond the
 * self-contained invariants {@code PartyTaxProfile}'s own constructor already enforces (e.g. a
 * {@code GST_REGISTERED} party must have a GST number). Applications extend this for their own
 * additional checks (e.g. a GST number format check) without touching the resolver.
 */
public interface RegistrationPolicy extends Extension {

  /**
   * Validates one party's profile.
   *
   * @throws InvalidTaxProfileException if {@code profile} is not usable for resolution.
   */
  void validate(PartyTaxProfile profile);
}
