package io.genfin.reconciliation.internal.rule;

import io.genfin.api.exception.Severity;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.port.rule.ReconciliationRule;
import io.genfin.reconciliation.rule.RuleContext;
import io.genfin.reconciliation.rule.RuleResult;
import java.util.List;

/** Both sides must carry the same {@code Reference}, when both carry one at all. */
public final class ReferencesEqualRule implements ReconciliationRule {

  public static final String CODE = "REFERENCES_EQUAL";

  @Override
  public List<RuleResult> evaluate(
      ComparisonRecord left, ComparisonRecord right, RuleContext context) {
    if (left.reference() == null || right.reference() == null) {
      return List.of();
    }
    if (left.reference().equals(right.reference())) {
      return List.of();
    }
    return List.of(
        RuleResult.of(
            CODE,
            "References differ: " + left.reference() + " vs " + right.reference() + ".",
            Severity.ERROR));
  }
}
