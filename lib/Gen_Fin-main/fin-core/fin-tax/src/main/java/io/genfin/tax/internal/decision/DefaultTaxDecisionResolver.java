package io.genfin.tax.internal.decision;

import io.genfin.api.validation.Validate;
import io.genfin.tax.api.decision.StandardTaxTreatment;
import io.genfin.tax.api.decision.TaxDecision;
import io.genfin.tax.api.decision.TaxTreatment;
import io.genfin.tax.api.party.PartyTaxProfile;
import io.genfin.tax.api.party.StandardCountryCode;
import io.genfin.tax.api.supply.StandardSupplyType;
import io.genfin.tax.api.supply.SupplyType;
import io.genfin.tax.api.transaction.TransactionContext;
import io.genfin.tax.port.DomesticPolicy;
import io.genfin.tax.port.ExportPolicy;
import io.genfin.tax.port.ImportPolicy;
import io.genfin.tax.port.RegistrationPolicy;
import io.genfin.tax.port.TaxDecisionResolver;

/**
 * Composes {@link DomesticPolicy}/{@link ExportPolicy}/{@link ImportPolicy} against the "only INDIA
 * is a supported jurisdiction" rule from the design: seller/buyer country determines which policy
 * (if any) applies; every pairing that isn't Rule 1-4 falls through to {@code NO_TAX} — the
 * "everything else returns NO_TAX through configurable strategies" requirement.
 */
public final class DefaultTaxDecisionResolver implements TaxDecisionResolver {

  private final RegistrationPolicy registrationPolicy;
  private final DomesticPolicy domesticPolicy;
  private final ExportPolicy exportPolicy;
  private final ImportPolicy importPolicy;

  public DefaultTaxDecisionResolver(
      RegistrationPolicy registrationPolicy,
      DomesticPolicy domesticPolicy,
      ExportPolicy exportPolicy,
      ImportPolicy importPolicy) {
    this.registrationPolicy =
        Validate.notNull(registrationPolicy, "registrationPolicy must not be null.");
    this.domesticPolicy = Validate.notNull(domesticPolicy, "domesticPolicy must not be null.");
    this.exportPolicy = Validate.notNull(exportPolicy, "exportPolicy must not be null.");
    this.importPolicy = Validate.notNull(importPolicy, "importPolicy must not be null.");
  }

  @Override
  public TaxDecision resolve(
      PartyTaxProfile seller, PartyTaxProfile buyer, TransactionContext context) {
    Validate.notNull(seller, "seller must not be null.");
    Validate.notNull(buyer, "buyer must not be null.");
    Validate.notNull(context, "context must not be null.");
    registrationPolicy.validate(seller);
    registrationPolicy.validate(buyer);

    boolean sellerIndia = StandardCountryCode.INDIA.code().equals(seller.country().code());
    boolean buyerIndia = StandardCountryCode.INDIA.code().equals(buyer.country().code());

    if (sellerIndia && buyerIndia) {
      TaxTreatment treatment = domesticPolicy.decide(seller, buyer);
      SupplyType supplyType =
          StandardTaxTreatment.CGST_SGST.code().equals(treatment.code())
              ? StandardSupplyType.DOMESTIC
              : StandardSupplyType.INTERSTATE;
      return TaxDecision.of(treatment, supplyType);
    }
    if (sellerIndia) {
      return TaxDecision.of(exportPolicy.decide(seller, buyer), StandardSupplyType.EXPORT);
    }
    if (buyerIndia) {
      return TaxDecision.of(importPolicy.decide(seller, buyer), StandardSupplyType.IMPORT);
    }
    return TaxDecision.of(StandardTaxTreatment.NO_TAX, StandardSupplyType.NONE);
  }
}
