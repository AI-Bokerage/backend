package io.genfin.reconciliation.internal.rule;

import io.genfin.api.exception.Severity;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.port.rule.ReconciliationRule;
import io.genfin.reconciliation.rule.RuleContext;
import io.genfin.reconciliation.rule.RuleResult;
import java.util.List;

/**
 * Fires when the caller has resolved (via {@code context.refundExists()}) that a refund expected
 * against this item could not be found. Not applicable when the caller left it unresolved.
 */
public final class RefundExistsRule implements ReconciliationRule {

  public static final String CODE = "REFUND_EXISTS";

  @Override
  public List<RuleResult> evaluate(
      ComparisonRecord left, ComparisonRecord right, RuleContext context) {
    if (context.refundExists() == null || context.refundExists()) {
      return List.of();
    }
    return List.of(RuleResult.of(CODE, "Expected refund was not found.", Severity.ERROR));
  }
}
