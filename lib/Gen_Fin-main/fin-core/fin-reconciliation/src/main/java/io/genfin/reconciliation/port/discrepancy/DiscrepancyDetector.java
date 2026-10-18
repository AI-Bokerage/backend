package io.genfin.reconciliation.port.discrepancy;

import io.genfin.api.port.spi.Extension;
import io.genfin.reconciliation.comparison.ComparisonResult;
import io.genfin.reconciliation.discrepancy.Discrepancy;
import java.util.List;

/**
 * Turns a {@link ComparisonResult} into the {@link Discrepancy} records worth reporting, by running
 * its configured {@link DiscrepancyPolicy} over every {@code Difference} found.
 */
public interface DiscrepancyDetector extends Extension {

  List<Discrepancy> detect(ComparisonResult result);
}
