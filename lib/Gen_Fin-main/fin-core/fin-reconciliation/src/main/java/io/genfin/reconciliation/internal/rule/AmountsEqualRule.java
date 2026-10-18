package io.genfin.reconciliation.internal.rule;

import io.genfin.api.exception.Severity;
import io.genfin.money.money.Money;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.port.rule.ReconciliationRule;
import io.genfin.reconciliation.rule.RuleContext;
import io.genfin.reconciliation.rule.RuleResult;
import java.util.List;

/**
 * Both sides must carry the exact same {@link Money} amount. Stays silent when either side has no
 * amount or the currencies already differ — {@link CurrenciesEqualRule} reports that instead so the
 * two never double-report the same underlying problem.
 */
public final class AmountsEqualRule implements ReconciliationRule {

  public static final String CODE = "AMOUNTS_EQUAL";

  @Override
  public List<RuleResult> evaluate(
      ComparisonRecord left, ComparisonRecord right, RuleContext context) {
    if (left.amount() == null || right.amount() == null) {
      return List.of();
    }
    if (!left.amount().currency().equals(right.amount().currency())) {
      return List.of();
    }
    if (left.amount().equals(right.amount())) {
      return List.of();
    }
    return List.of(
        RuleResult.of(
            CODE,
            "Amounts differ: " + left.amount() + " vs " + right.amount() + ".",
            Severity.ERROR));
  }
}
