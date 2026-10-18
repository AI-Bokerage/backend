package io.genfin.reconciliation.internal.rule;

import io.genfin.api.exception.Severity;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.port.rule.ReconciliationRule;
import io.genfin.reconciliation.rule.RuleContext;
import io.genfin.reconciliation.rule.RuleResult;
import java.util.List;

/**
 * Fires when the caller has resolved (via {@code context.paymentExists()}) that no internal payment
 * was found for an external record (e.g. a provider settlement or bank transaction) that expects
 * one.
 */
public final class MissingPaymentRule implements ReconciliationRule {

  public static final String CODE = "MISSING_PAYMENT";

  @Override
  public List<RuleResult> evaluate(
      ComparisonRecord left, ComparisonRecord right, RuleContext context) {
    if (context.paymentExists() == null || context.paymentExists()) {
      return List.of();
    }
    return List.of(
        RuleResult.of(CODE, "No internal payment was found for this record.", Severity.ERROR));
  }
}
