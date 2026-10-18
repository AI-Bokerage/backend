package io.genfin.reconciliation.internal.calculation;

import io.genfin.api.validation.Validate;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import io.genfin.reconciliation.discrepancy.Discrepancy;
import io.genfin.reconciliation.discrepancy.DiscrepancySeverity;
import io.genfin.reconciliation.matching.MatchResult;
import io.genfin.reconciliation.port.calculation.SummaryCalculator;
import io.genfin.reconciliation.reconciliation.Reconciliation;
import io.genfin.reconciliation.summary.ReconciliationStatistics;
import io.genfin.reconciliation.summary.ReconciliationSummary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tallies match outcomes and discrepancy severities with a single pass over each list — no
 * short-circuiting, every match and every discrepancy is counted.
 */
public final class DefaultSummaryCalculator implements SummaryCalculator {

  @Override
  public ReconciliationSummary summarize(
      Reconciliation reconciliation, List<MatchResult> matches, List<Discrepancy> discrepancies) {
    Validate.notNull(reconciliation, "reconciliation must not be null.");
    Validate.notNull(matches, "matches must not be null.");
    Validate.notNull(discrepancies, "discrepancies must not be null.");

    int matched = 0;
    int partiallyMatched = 0;
    int unmatched = 0;
    Map<Currency, Money> differenceTotals = new LinkedHashMap<>();
    for (MatchResult match : matches) {
      switch (match.outcome()) {
        case MATCHED -> matched++;
        case PARTIALLY_MATCHED -> partiallyMatched++;
        case NOT_MATCHED -> unmatched++;
      }
      match
          .varianceAmount()
          .ifPresent(
              variance ->
                  differenceTotals.merge(
                      variance.currency(),
                      variance.abs(),
                      (existing, added) -> existing.add(added)));
    }

    Map<DiscrepancySeverity, Long> countsBySeverity = new LinkedHashMap<>();
    for (Discrepancy discrepancy : discrepancies) {
      countsBySeverity.merge(discrepancy.severity(), 1L, Long::sum);
    }

    int attempts = matches.size();
    double matchRate = attempts == 0 ? 0.0 : (double) matched / attempts;

    ReconciliationStatistics statistics =
        new ReconciliationStatistics(
            reconciliation.items().size(), attempts, discrepancies.size(), matchRate);

    return new ReconciliationSummary(
        matched,
        partiallyMatched,
        unmatched,
        discrepancies.size(),
        differenceTotals,
        countsBySeverity,
        statistics);
  }
}
