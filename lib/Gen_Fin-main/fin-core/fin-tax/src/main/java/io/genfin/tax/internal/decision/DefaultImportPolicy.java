package io.genfin.tax.internal.decision;

import io.genfin.tax.api.decision.StandardTaxTreatment;
import io.genfin.tax.api.decision.TaxTreatment;
import io.genfin.tax.api.party.PartyTaxProfile;
import io.genfin.tax.port.ImportPolicy;

/** Rule 4: always {@code IMPORT_SERVICE} — no calculation in V1. */
public final class DefaultImportPolicy implements ImportPolicy {

  @Override
  public TaxTreatment decide(PartyTaxProfile seller, PartyTaxProfile buyer) {
    return StandardTaxTreatment.IMPORT_SERVICE;
  }
}
