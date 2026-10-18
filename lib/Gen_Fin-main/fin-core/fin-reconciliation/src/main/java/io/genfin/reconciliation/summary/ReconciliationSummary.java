package io.genfin.reconciliation.summary;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import io.genfin.reconciliation.discrepancy.DiscrepancySeverity;
import java.util.Map;

/**
 * A derived, point-in-time rollup of one {@link
 * io.genfin.reconciliation.reconciliation.Reconciliation} — how many items matched, partially
 * matched, or failed to match, what the outstanding differences add up to (per currency, since a
 * reconciliation run may span several), how discrepancies break down by severity, and general
 * statistics. Produced by a {@code SummaryCalculator}; this type itself performs no calculation.
 */
public record ReconciliationSummary(
    int matchedCount,
    int partiallyMatchedCount,
    int unmatchedCount,
    int discrepancyCount,
    Map<Currency, Money> differenceTotalsByCurrency,
    Map<DiscrepancySeverity, Long> discrepancyCountsBySeverity,
    ReconciliationStatistics statistics)
    implements ValueObject {

  public ReconciliationSummary {
    Validate.nonNegative(matchedCount, "matchedCount must not be negative.");
    Validate.nonNegative(partiallyMatchedCount, "partiallyMatchedCount must not be negative.");
    Validate.nonNegative(unmatchedCount, "unmatchedCount must not be negative.");
    Validate.nonNegative(discrepancyCount, "discrepancyCount must not be negative.");
    Validate.notNull(statistics, "statistics must not be null.");
    differenceTotalsByCurrency = Map.copyOf(differenceTotalsByCurrency);
    discrepancyCountsBySeverity = Map.copyOf(discrepancyCountsBySeverity);
  }

  /** Total number of match attempts this summary was derived from. */
  public int totalMatchAttempts() {
    return matchedCount + partiallyMatchedCount + unmatchedCount;
  }

  /** True when every match attempt fully matched and no discrepancy remains open. */
  public boolean isFullyReconciled() {
    return unmatchedCount == 0 && partiallyMatchedCount == 0 && discrepancyCount == 0;
  }
}
