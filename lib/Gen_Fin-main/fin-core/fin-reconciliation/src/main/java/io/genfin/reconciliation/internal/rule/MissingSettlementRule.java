package io.genfin.reconciliation.internal.rule;

import io.genfin.api.exception.Severity;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.port.rule.ReconciliationRule;
import io.genfin.reconciliation.rule.RuleContext;
import io.genfin.reconciliation.rule.RuleResult;
import java.util.List;

/**
 * Fires when the caller has resolved (via {@code context.settlementExists()}) that no provider
 * settlement was found for an internal payment that expects one.
 */
public final class MissingSettlementRule implements ReconciliationRule {

  public static final String CODE = "MISSING_SETTLEMENT";

  @Override
  public List<RuleResult> evaluate(
      ComparisonRecord left, ComparisonRecord right, RuleContext context) {
    if (context.settlementExists() == null || context.settlementExists()) {
      return List.of();
    }
    return List.of(
        RuleResult.of(CODE, "No provider settlement was found for this payment.", Severity.ERROR));
  }
}
