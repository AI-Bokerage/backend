package io.genfin.tax.port;

import io.genfin.api.port.spi.Extension;
import io.genfin.tax.api.decision.TaxDecision;
import io.genfin.tax.api.party.PartyTaxProfile;
import io.genfin.tax.api.transaction.TransactionContext;

/**
 * Resolves which {@link TaxDecision} applies to a seller/buyer pair — resolution only, no rates and
 * no amounts. That's {@link TaxRateProvider} and {@code io.genfin.money.port.tax.TaxCalculator},
 * one stage later, per this phase's decision → rate → calculation pipeline.
 */
public interface TaxDecisionResolver extends Extension {

  TaxDecision resolve(PartyTaxProfile seller, PartyTaxProfile buyer, TransactionContext context);
}
