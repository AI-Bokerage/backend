package io.genfin.dunning.internal.rule;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.port.rule.CollectionRule;
import io.genfin.dunning.rule.CollectionDecision;
import io.genfin.dunning.rule.CollectionRuleContext;
import io.genfin.dunning.rule.StandardCollectionOutcome;
import io.genfin.money.money.Money;
import java.util.List;

/**
 * Illustrative example rule: flags an obligation for priority handling once its amount reaches an
 * application-supplied {@code priorityThreshold}. Unlike the other example rules, "what counts as
 * high priority" is never a number fin-dunning could sensibly default - so this rule is always
 * constructed with the caller's own threshold, never a bare literal. Stays silent when the
 * obligation's currency does not match the threshold's.
 */
public final class PriorityCollectionRule implements CollectionRule {

  public static final String CODE = "PRIORITY_AMOUNT";

  private final Money priorityThreshold;

  public PriorityCollectionRule(Money priorityThreshold) {
    this.priorityThreshold =
        Validate.notNull(priorityThreshold, "priorityThreshold must not be null.");
  }

  @Override
  public List<CollectionDecision> evaluate(CollectionRuleContext context) {
    Money amount = context.obligation().amount();
    if (!amount.currency().equals(priorityThreshold.currency())) {
      return List.of();
    }
    if (amount.compareTo(priorityThreshold) < 0) {
      return List.of();
    }
    return List.of(
        CollectionDecision.of(
            CODE,
            StandardCollectionOutcome.PRIORITIZE,
            "Obligation amount "
                + amount
                + " meets or exceeds the priority threshold "
                + priorityThreshold
                + "."));
  }
}
