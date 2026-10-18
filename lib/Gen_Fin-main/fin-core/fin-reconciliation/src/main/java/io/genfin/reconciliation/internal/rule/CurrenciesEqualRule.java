package io.genfin.reconciliation.internal.rule;

import io.genfin.api.exception.Severity;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.port.rule.ReconciliationRule;
import io.genfin.reconciliation.rule.RuleContext;
import io.genfin.reconciliation.rule.RuleResult;
import java.util.List;

/** Both sides must carry an amount in the same currency; no tolerance ever applies here. */
public final class CurrenciesEqualRule implements ReconciliationRule {

  public static final String CODE = "CURRENCIES_EQUAL";

  @Override
  public List<RuleResult> evaluate(
      ComparisonRecord left, ComparisonRecord right, RuleContext context) {
    if (left.amount() == null || right.amount() == null) {
      return List.of();
    }
    if (left.amount().currency().equals(right.amount().currency())) {
      return List.of();
    }
    return List.of(
        RuleResult.of(
            CODE,
            "Currencies differ: "
                + left.amount().currency().code()
                + " vs "
                + right.amount().currency().code()
                + ".",
            Severity.CRITICAL));
  }
}
