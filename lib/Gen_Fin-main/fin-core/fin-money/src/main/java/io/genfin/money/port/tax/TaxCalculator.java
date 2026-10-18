package io.genfin.money.port.tax;

import io.genfin.api.port.spi.Extension;
import io.genfin.api.result.Result;
import io.genfin.money.tax.TaxComputationResult;
import io.genfin.money.tax.TaxContext;

/**
 * Computes a full {@link io.genfin.money.tax.TaxBreakdown} for a {@link TaxContext}. Implemented
 * entirely outside this module.
 */
public interface TaxCalculator extends Extension {

  Result<TaxComputationResult> calculate(TaxContext context);
}
