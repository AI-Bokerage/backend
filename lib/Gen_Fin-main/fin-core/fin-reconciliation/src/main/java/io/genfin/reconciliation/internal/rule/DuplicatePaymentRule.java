package io.genfin.reconciliation.internal.rule;

import io.genfin.api.exception.Severity;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.port.rule.ReconciliationRule;
import io.genfin.reconciliation.rule.RuleContext;
import io.genfin.reconciliation.rule.RuleResult;
import java.util.List;

/**
 * Fires when the caller has resolved (via {@code context.duplicatePayment()}) that this item's
 * payment reference already appears elsewhere in the batch.
 */
public final class DuplicatePaymentRule implements ReconciliationRule {

  public static final String CODE = "DUPLICATE_PAYMENT";

  @Override
  public List<RuleResult> evaluate(
      ComparisonRecord left, ComparisonRecord right, RuleContext context) {
    if (context.duplicatePayment() == null || !context.duplicatePayment()) {
      return List.of();
    }
    return List.of(
        RuleResult.of(CODE, "Payment reference is duplicated in the batch.", Severity.ERROR));
  }
}
