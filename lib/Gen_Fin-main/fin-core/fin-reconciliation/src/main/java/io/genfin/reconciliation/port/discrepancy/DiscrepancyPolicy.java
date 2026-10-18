package io.genfin.reconciliation.port.discrepancy;

import io.genfin.api.port.spi.Extension;
import io.genfin.reconciliation.comparison.Difference;
import io.genfin.reconciliation.discrepancy.Discrepancy;
import java.util.Optional;

/**
 * Decides whether a single {@link Difference} rises to a reportable {@link Discrepancy} and, if so,
 * how to classify it (category, severity, reason). Returns {@code Optional.empty()} for a
 * difference the policy considers explainable/expected — e.g. an amount gap still inside tolerance
 * — so it never becomes a discrepancy in the first place.
 */
public interface DiscrepancyPolicy extends Extension {

  Optional<Discrepancy> classify(Difference difference);
}
