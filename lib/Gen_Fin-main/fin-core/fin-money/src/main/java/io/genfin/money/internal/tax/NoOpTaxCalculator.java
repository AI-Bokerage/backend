package io.genfin.money.internal.tax;

import io.genfin.api.result.Result;
import io.genfin.money.port.tax.TaxCalculator;
import io.genfin.money.tax.TaxBreakdown;
import io.genfin.money.tax.TaxComputationResult;
import io.genfin.money.tax.TaxContext;

/**
 * Default calculator for contexts with taxation disabled or not yet configured — always zero tax.
 */
public final class NoOpTaxCalculator implements TaxCalculator {

  @Override
  public Result<TaxComputationResult> calculate(TaxContext context) {
    TaxBreakdown breakdown = TaxBreakdown.none(context.taxableAmount().amount().currency());
    return Result.success(new TaxComputationResult(context, breakdown));
  }
}
