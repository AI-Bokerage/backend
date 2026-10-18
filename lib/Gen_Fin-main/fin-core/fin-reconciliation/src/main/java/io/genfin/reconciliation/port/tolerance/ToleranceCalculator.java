package io.genfin.reconciliation.port.tolerance;

import io.genfin.api.port.spi.Extension;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.tolerance.Tolerance;
import io.genfin.reconciliation.tolerance.ToleranceResult;

/**
 * Evaluates one {@link Tolerance} against two sides of a comparison — the single entry point a
 * {@code MatchingStrategy} (e.g. {@code ToleranceMatch}) or a {@code ComparisonStrategy} calls into
 * instead of reading threshold values itself. Never hardcodes a threshold: every value comes from
 * the {@link Tolerance} instance supplied, which is itself built from configuration.
 */
public interface ToleranceCalculator extends Extension {

  ToleranceResult evaluate(Tolerance tolerance, ComparisonRecord left, ComparisonRecord right);
}
