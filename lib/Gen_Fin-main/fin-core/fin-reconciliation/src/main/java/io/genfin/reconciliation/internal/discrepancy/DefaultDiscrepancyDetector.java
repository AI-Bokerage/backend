package io.genfin.reconciliation.internal.discrepancy;

import io.genfin.api.validation.Validate;
import io.genfin.reconciliation.comparison.ComparisonResult;
import io.genfin.reconciliation.discrepancy.Discrepancy;
import io.genfin.reconciliation.port.discrepancy.DiscrepancyDetector;
import io.genfin.reconciliation.port.discrepancy.DiscrepancyPolicy;
import java.util.ArrayList;
import java.util.List;

/** Runs the configured {@link DiscrepancyPolicy} over every {@code Difference} in the result. */
public final class DefaultDiscrepancyDetector implements DiscrepancyDetector {

  private final DiscrepancyPolicy policy;

  public DefaultDiscrepancyDetector(DiscrepancyPolicy policy) {
    this.policy = Validate.notNull(policy, "policy must not be null.");
  }

  @Override
  public List<Discrepancy> detect(ComparisonResult result) {
    Validate.notNull(result, "result must not be null.");
    List<Discrepancy> discrepancies = new ArrayList<>();
    for (var difference : result.differences()) {
      policy.classify(difference).ifPresent(discrepancies::add);
    }
    return List.copyOf(discrepancies);
  }
}
