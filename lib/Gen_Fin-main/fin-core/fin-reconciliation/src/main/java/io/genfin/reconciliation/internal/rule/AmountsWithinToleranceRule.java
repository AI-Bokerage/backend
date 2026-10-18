package io.genfin.reconciliation.internal.rule;

import io.genfin.api.exception.Severity;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.port.rule.ReconciliationRule;
import io.genfin.reconciliation.rule.RuleContext;
import io.genfin.reconciliation.rule.RuleResult;
import io.genfin.reconciliation.tolerance.ToleranceOutcome;
import io.genfin.reconciliation.tolerance.ToleranceResult;
import java.util.List;

/**
 * Delegates to {@code context.toleranceCalculator()} for {@code context.amountTolerance()}; not
 * applicable at all unless the caller supplied both.
 */
public final class AmountsWithinToleranceRule implements ReconciliationRule {

  public static final String CODE = "AMOUNTS_WITHIN_TOLERANCE";

  @Override
  public List<RuleResult> evaluate(
      ComparisonRecord left, ComparisonRecord right, RuleContext context) {
    if (context.amountTolerance() == null || context.toleranceCalculator() == null) {
      return List.of();
    }
    ToleranceResult result =
        context.toleranceCalculator().evaluate(context.amountTolerance(), left, right);
    if (result.outcome() != ToleranceOutcome.EXCEEDS_TOLERANCE) {
      return List.of();
    }
    return List.of(RuleResult.of(CODE, result.explanation(), Severity.ERROR));
  }
}
