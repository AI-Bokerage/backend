package io.genfin.reconciliation.port.calculation;

import io.genfin.api.port.spi.Extension;
import io.genfin.reconciliation.discrepancy.Discrepancy;
import io.genfin.reconciliation.matching.MatchResult;
import io.genfin.reconciliation.reconciliation.Reconciliation;
import io.genfin.reconciliation.summary.ReconciliationSummary;
import java.util.List;

/**
 * Derives a {@link ReconciliationSummary} from a {@link Reconciliation}'s items together with the
 * {@link MatchResult}s and {@link Discrepancy}s found while working it — the aggregate itself keeps
 * only lightweight identifiers/notes, so the richer objects are supplied alongside it.
 */
public interface SummaryCalculator extends Extension {

  ReconciliationSummary summarize(
      Reconciliation reconciliation, List<MatchResult> matches, List<Discrepancy> discrepancies);
}
