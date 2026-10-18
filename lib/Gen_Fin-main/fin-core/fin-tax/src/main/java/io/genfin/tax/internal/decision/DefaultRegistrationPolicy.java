package io.genfin.tax.internal.decision;

import io.genfin.tax.api.exception.InvalidTaxProfileException;
import io.genfin.tax.api.party.PartyTaxProfile;
import io.genfin.tax.api.party.StandardCountryCode;
import io.genfin.tax.api.party.StandardRegistrationType;
import io.genfin.tax.port.RegistrationPolicy;

/**
 * A GST-registered Indian party must have a state on file — the domestic CGST/SGST-vs-IGST decision
 * (Rule 1/2) depends on comparing states, and an Indian GST registration is always issued against
 * one specific state.
 */
public final class DefaultRegistrationPolicy implements RegistrationPolicy {

  @Override
  public void validate(PartyTaxProfile profile) {
    boolean isIndianGstRegistered =
        StandardCountryCode.INDIA.code().equals(profile.country().code())
            && StandardRegistrationType.GST_REGISTERED
                .code()
                .equals(profile.registrationType().code());
    if (isIndianGstRegistered && profile.stateOptional().isEmpty()) {
      throw new InvalidTaxProfileException(
          "A GST_REGISTERED party in India must have a state on file.");
    }
  }
}
