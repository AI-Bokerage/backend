package io.genfin.dunning.internal.rule;

import io.genfin.dunning.port.rule.CollectionRule;
import io.genfin.dunning.rule.CollectionDecision;
import io.genfin.dunning.rule.CollectionRuleContext;
import io.genfin.dunning.rule.StandardCollectionOutcome;
import java.util.List;

/**
 * Illustrative example rule: fires while the obligation is still within its resolved {@code
 * GracePeriod} - i.e. dunning has not become due yet. Stays silent when the context carries no
 * grace period to check against; never hardcodes a grace span of its own.
 */
public final class GracePeriodActiveRule implements CollectionRule {

  public static final String CODE = "GRACE_PERIOD_ACTIVE";

  @Override
  public List<CollectionDecision> evaluate(CollectionRuleContext context) {
    if (context.gracePeriod() == null) {
      return List.of();
    }
    if (context.gracePeriod().hasExpired(context.obligation().dueDate(), context.asOf())) {
      return List.of();
    }
    return List.of(
        CollectionDecision.of(
            CODE,
            StandardCollectionOutcome.SKIP,
            "Obligation is still within its configured grace period."));
  }
}
