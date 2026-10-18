package io.genfin.reconciliation.rule;

import io.genfin.reconciliation.internal.rule.DefaultRuleEngine;
import io.genfin.reconciliation.port.rule.ReconciliationRule;
import io.genfin.reconciliation.port.rule.RuleEngine;
import java.util.List;

/** Factory for the default {@link RuleEngine} implementation. */
public final class RuleEngines {

  private RuleEngines() {}

  /** An engine carrying every {@link ReconciliationRules#defaultRules()}. */
  public static RuleEngine standard() {
    return new DefaultRuleEngine(ReconciliationRules.defaultRules());
  }

  public static RuleEngine of(List<ReconciliationRule> rules) {
    return new DefaultRuleEngine(rules);
  }
}
