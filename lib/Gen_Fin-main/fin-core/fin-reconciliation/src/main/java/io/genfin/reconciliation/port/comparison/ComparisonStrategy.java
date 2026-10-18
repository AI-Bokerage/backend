package io.genfin.reconciliation.port.comparison;

import io.genfin.api.port.spi.Extension;
import io.genfin.reconciliation.comparison.ComparisonContext;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.comparison.Difference;
import java.util.List;

/**
 * Compares a single dimension (amount, currency, reference, status, timestamp, metadata, a custom
 * attribute, ...) of two {@link ComparisonRecord}s. Returns an empty list when that dimension
 * agrees — a strategy never reports "no difference" any other way.
 */
public interface ComparisonStrategy extends Extension {

  List<Difference> compare(
      ComparisonRecord left, ComparisonRecord right, ComparisonContext context);
}
