package io.genfin.tax.internal.calculation;

import io.genfin.api.result.Result;
import io.genfin.api.validation.Validate;
import io.genfin.money.port.tax.TaxCalculator;
import io.genfin.money.tax.TaxCalculators;
import io.genfin.money.tax.TaxComputationResult;
import io.genfin.money.tax.TaxContext;
import io.genfin.tax.api.exception.TaxErrorCode;

/**
 * Wraps a configured tax engine ({@link IndianTaxCalculator}, or any other {@code TaxCalculator})
 * while preserving {@code fin-money}'s zero-config default behaviour: a caller that never supplies
 * a seller/buyer tax profile in {@code TaxContext.metadata()} (i.e. isn't using the Tax Engine at
 * all) gets exactly the same zero-tax result {@code NoOpTaxCalculator} always gave, rather than a
 * failure. Any other kind of failure (e.g. a genuinely misconfigured rate table) still propagates —
 * only the "tax engine wasn't used for this call" case falls back silently.
 */
public final class DelegatingTaxCalculator implements TaxCalculator {

  private final TaxCalculator engine;
  private final TaxCalculator fallback;

  public DelegatingTaxCalculator(TaxCalculator engine) {
    this.engine = Validate.notNull(engine, "engine must not be null.");
    this.fallback = TaxCalculators.noOp();
  }

  @Override
  public Result<TaxComputationResult> calculate(TaxContext context) {
    Result<TaxComputationResult> result = engine.calculate(context);
    return result.fold(
        Result::success,
        failure ->
            TaxErrorCode.MISSING_TAX_PROFILE.code().equals(failure.errorCode().code())
                ? fallback.calculate(context)
                : result);
  }
}
