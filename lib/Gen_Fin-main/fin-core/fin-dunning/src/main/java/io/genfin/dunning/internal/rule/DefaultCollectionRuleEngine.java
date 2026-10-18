package io.genfin.dunning.internal.rule;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.port.rule.CollectionRule;
import io.genfin.dunning.port.rule.CollectionRuleEngine;
import io.genfin.dunning.rule.CollectionDecision;
import io.genfin.dunning.rule.CollectionRuleContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs every configured rule and collects all decisions - never short-circuits on the first rule
 * that reports something, mirroring {@code
 * io.genfin.reconciliation.internal.rule.DefaultRuleEngine}.
 */
public final class DefaultCollectionRuleEngine implements CollectionRuleEngine {

  private final List<CollectionRule> rules;

  public DefaultCollectionRuleEngine(List<CollectionRule> rules) {
    this.rules = List.copyOf(rules);
  }

  @Override
  public List<CollectionDecision> evaluate(CollectionRuleContext context) {
    Validate.notNull(context, "context must not be null.");
    List<CollectionDecision> decisions = new ArrayList<>();
    for (CollectionRule rule : rules) {
      decisions.addAll(rule.evaluate(context));
    }
    return List.copyOf(decisions);
  }
}
