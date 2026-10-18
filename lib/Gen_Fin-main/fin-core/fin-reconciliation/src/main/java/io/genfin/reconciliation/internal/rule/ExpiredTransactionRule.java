package io.genfin.reconciliation.internal.rule;

import io.genfin.api.exception.Severity;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.port.rule.ReconciliationRule;
import io.genfin.reconciliation.rule.RuleContext;
import io.genfin.reconciliation.rule.RuleResult;
import java.util.List;

/**
 * Fires when the caller has resolved (via {@code context.expired()}) that this transaction has aged
 * past the window in which it is still expected to reconcile.
 */
public final class ExpiredTransactionRule implements ReconciliationRule {

  public static final String CODE = "EXPIRED_TRANSACTION";

  @Override
  public List<RuleResult> evaluate(
      ComparisonRecord left, ComparisonRecord right, RuleContext context) {
    if (context.expired() == null || !context.expired()) {
      return List.of();
    }
    return List.of(
        RuleResult.of(
            CODE,
            "Transaction has expired and is no longer expected to reconcile.",
            Severity.WARNING));
  }
}
