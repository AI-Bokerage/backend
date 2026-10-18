package io.genfin.tax.api.decision;

import io.genfin.api.validation.Validate;
import io.genfin.tax.api.supply.SupplyType;

/**
 * The output of tax <em>resolution</em> — which treatment applies and why (the {@link SupplyType}
 * that led to it). Carries no rates or amounts; that's the calculator's job, one stage later.
 */
public record TaxDecision(TaxTreatment treatment, SupplyType supplyType) {

  public TaxDecision {
    Validate.notNull(treatment, "treatment must not be null.");
    Validate.notNull(supplyType, "supplyType must not be null.");
  }

  public static TaxDecision of(TaxTreatment treatment, SupplyType supplyType) {
    return new TaxDecision(treatment, supplyType);
  }
}
