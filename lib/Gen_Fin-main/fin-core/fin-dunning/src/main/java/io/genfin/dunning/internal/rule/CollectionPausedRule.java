package io.genfin.dunning.internal.rule;

import io.genfin.dunning.port.rule.CollectionRule;
import io.genfin.dunning.rule.CollectionDecision;
import io.genfin.dunning.rule.CollectionRuleContext;
import io.genfin.dunning.rule.StandardCollectionOutcome;
import java.util.List;

/**
 * Illustrative example rule: fires when the case's {@code CollectionAttributes} have been marked
 * suspended by the application (e.g. a dispute is open). Stays silent when the context carries no
 * attributes at all.
 */
public final class CollectionPausedRule implements CollectionRule {

  public static final String CODE = "COLLECTION_PAUSED";

  @Override
  public List<CollectionDecision> evaluate(CollectionRuleContext context) {
    if (context.attributes() == null || !context.attributes().suspended()) {
      return List.of();
    }
    return List.of(
        CollectionDecision.of(
            CODE, StandardCollectionOutcome.PAUSE, "Collection has been suspended for this case."));
  }
}
