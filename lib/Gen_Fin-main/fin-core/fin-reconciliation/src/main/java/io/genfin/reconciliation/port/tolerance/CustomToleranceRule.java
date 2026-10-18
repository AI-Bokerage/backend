package io.genfin.reconciliation.port.tolerance;

import io.genfin.api.port.spi.Extension;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.tolerance.CustomTolerance;
import io.genfin.reconciliation.tolerance.ToleranceResult;

/**
 * One caller-defined tolerance rule, registered under a name a {@link CustomTolerance} carries so a
 * {@link ToleranceCalculator} can resolve it without knowing anything about it structurally — the
 * generic custom-tolerance extension point.
 */
public interface CustomToleranceRule extends Extension {

  String name();

  ToleranceResult evaluate(ComparisonRecord left, ComparisonRecord right);
}
