package io.genfin.reconciliation.internal.rule;

import io.genfin.api.validation.Validate;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.port.rule.ReconciliationRule;
import io.genfin.reconciliation.port.rule.RuleEngine;
import io.genfin.reconciliation.rule.RuleContext;
import io.genfin.reconciliation.rule.RuleResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs every configured rule and collects all results — never short-circuits on the first rule that
 * reports a problem, mirroring fin-refund's {@code DefaultRefundValidator}.
 */
public final class DefaultRuleEngine implements RuleEngine {

  private final List<ReconciliationRule> rules;

  public DefaultRuleEngine(List<ReconciliationRule> rules) {
    this.rules = List.copyOf(rules);
  }

  @Override
  public List<RuleResult> evaluate(
      ComparisonRecord left, ComparisonRecord right, RuleContext context) {
    Validate.notNull(left, "left must not be null.");
    Validate.notNull(right, "right must not be null.");
    Validate.notNull(context, "context must not be null.");
    List<RuleResult> results = new ArrayList<>();
    for (ReconciliationRule rule : rules) {
      results.addAll(rule.evaluate(left, right, context));
    }
    return List.copyOf(results);
  }
}
