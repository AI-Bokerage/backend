package io.genfin.tax.port;

import io.genfin.api.port.spi.Extension;
import io.genfin.tax.api.decision.TaxTreatment;
import io.genfin.tax.api.party.PartyTaxProfile;

/**
 * Decides between {@code EXPORT_LUT} and {@code EXPORT_IGST} for Rule 3 (seller in jurisdiction,
 * buyer foreign).
 */
public interface ExportPolicy extends Extension {

  TaxTreatment decide(PartyTaxProfile seller, PartyTaxProfile buyer);
}
