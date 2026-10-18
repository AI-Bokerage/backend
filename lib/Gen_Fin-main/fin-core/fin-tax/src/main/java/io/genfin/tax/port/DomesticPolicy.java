package io.genfin.tax.port;

import io.genfin.api.port.spi.Extension;
import io.genfin.tax.api.decision.TaxTreatment;
import io.genfin.tax.api.party.PartyTaxProfile;

/**
 * Decides between {@code CGST_SGST} (Rule 1, same state) and {@code IGST} (Rule 2, different state)
 * for a seller/buyer pair already known to be in the same supported jurisdiction.
 */
public interface DomesticPolicy extends Extension {

  TaxTreatment decide(PartyTaxProfile seller, PartyTaxProfile buyer);
}
