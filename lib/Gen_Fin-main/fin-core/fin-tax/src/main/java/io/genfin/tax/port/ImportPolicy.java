package io.genfin.tax.port;

import io.genfin.api.port.spi.Extension;
import io.genfin.tax.api.decision.TaxTreatment;
import io.genfin.tax.api.party.PartyTaxProfile;

/**
 * Decides the treatment for Rule 4 (seller foreign, buyer in jurisdiction). V1 always resolves to
 * {@code IMPORT_SERVICE} with no calculation — this policy exists so a future version can add real
 * reverse-charge handling without changing the resolver's shape.
 */
public interface ImportPolicy extends Extension {

  TaxTreatment decide(PartyTaxProfile seller, PartyTaxProfile buyer);
}
