package io.genfin.dunning.rule;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * One finding raised by a {@code CollectionRule} - e.g. "maximum retries reached", "grace period
 * still active", "collection paused", "today is a holiday", "priority amount". A rule that finds
 * nothing to report raises no {@code CollectionDecision} at all, mirroring {@code
 * io.genfin.reconciliation.rule.RuleResult}. Data only - a decision describes what a {@code
 * CollectionRuleEngine} found, it never skips, pauses, holds, or prioritizes anything itself; that
 * is left entirely to the consuming application.
 */
public record CollectionDecision(String ruleCode, CollectionOutcome outcome, String reason)
    implements ValueObject {

  public CollectionDecision {
    Validate.notBlank(ruleCode, "ruleCode must not be blank.");
    Validate.notNull(outcome, "outcome must not be null.");
    Validate.notBlank(reason, "reason must not be blank.");
  }

  public static CollectionDecision of(String ruleCode, CollectionOutcome outcome, String reason) {
    return new CollectionDecision(ruleCode, outcome, reason);
  }
}
