package io.genfin.dunning.internal.rule;

import io.genfin.dunning.port.rule.CollectionRule;
import io.genfin.dunning.rule.CollectionDecision;
import io.genfin.dunning.rule.CollectionRuleContext;
import io.genfin.dunning.rule.StandardCollectionOutcome;
import java.util.List;

/**
 * Illustrative example rule: fires once the obligation's retry history has already reached the
 * maximum attempt count its resolved {@code RetryPolicy} allows. Stays silent when the context
 * carries no retry history or no retry policy to check against - never assumes a count or a maximum
 * of its own, both always come from the caller's resolved policy.
 */
public final class MaxRetriesReachedRule implements CollectionRule {

  public static final String CODE = "MAX_RETRIES_REACHED";

  @Override
  public List<CollectionDecision> evaluate(CollectionRuleContext context) {
    if (context.retryHistory() == null || context.retryPolicy() == null) {
      return List.of();
    }
    if (context.retryHistory().size() < context.retryPolicy().maxRetries()) {
      return List.of();
    }
    return List.of(
        CollectionDecision.of(
            CODE,
            StandardCollectionOutcome.HOLD,
            "Retry history has reached the policy's maximum of "
                + context.retryPolicy().maxRetries()
                + " attempts."));
  }
}
