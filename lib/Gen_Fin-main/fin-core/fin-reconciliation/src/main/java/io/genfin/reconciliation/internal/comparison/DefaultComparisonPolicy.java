package io.genfin.reconciliation.internal.comparison;

import io.genfin.api.validation.Validate;
import io.genfin.reconciliation.comparison.ComparisonContext;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.comparison.ComparisonResult;
import io.genfin.reconciliation.comparison.Difference;
import io.genfin.reconciliation.port.comparison.ComparisonPolicy;
import io.genfin.reconciliation.port.comparison.ComparisonStrategy;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs every configured {@link ComparisonStrategy} and collects all differences — never stops at
 * the first one found.
 */
public final class DefaultComparisonPolicy implements ComparisonPolicy {

  private final List<ComparisonStrategy> strategies;

  public DefaultComparisonPolicy(List<ComparisonStrategy> strategies) {
    Validate.notNull(strategies, "strategies must not be null.");
    this.strategies = List.copyOf(strategies);
  }

  @Override
  public ComparisonResult evaluate(
      ComparisonRecord left, ComparisonRecord right, ComparisonContext context) {
    List<Difference> differences = new ArrayList<>();
    for (ComparisonStrategy strategy : strategies) {
      differences.addAll(strategy.compare(left, right, context));
    }
    return ComparisonResult.of(differences);
  }
}
