package io.genfin.reconciliation.port.comparison;

import io.genfin.api.port.spi.Extension;
import io.genfin.reconciliation.comparison.ComparisonContext;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.comparison.ComparisonResult;

/**
 * The single entry point for comparing two {@link ComparisonRecord}s: composes every configured
 * {@link ComparisonStrategy} and collects all their differences instead of stopping at the first
 * one found, so callers see the full picture in one call.
 */
public interface ComparisonPolicy extends Extension {

  ComparisonResult evaluate(
      ComparisonRecord left, ComparisonRecord right, ComparisonContext context);
}
