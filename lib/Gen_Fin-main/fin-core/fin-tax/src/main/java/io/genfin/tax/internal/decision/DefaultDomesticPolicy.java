package io.genfin.tax.internal.decision;

import io.genfin.tax.api.decision.StandardTaxTreatment;
import io.genfin.tax.api.decision.TaxTreatment;
import io.genfin.tax.api.party.PartyTaxProfile;
import io.genfin.tax.port.DomesticPolicy;

/** Rule 1/2: same state → {@code CGST_SGST}; different (or unresolvable) state → {@code IGST}. */
public final class DefaultDomesticPolicy implements DomesticPolicy {

  @Override
  public TaxTreatment decide(PartyTaxProfile seller, PartyTaxProfile buyer) {
    boolean sameState =
        seller.stateOptional().isPresent()
            && buyer.stateOptional().isPresent()
            && seller.state().equals(buyer.state());
    return sameState ? StandardTaxTreatment.CGST_SGST : StandardTaxTreatment.IGST;
  }
}
