package io.genfin.dunning.rule;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.dunning.calendar.GracePeriod;
import io.genfin.dunning.collection.CollectionAttributes;
import io.genfin.dunning.lifecycle.DunningCaseStatus;
import io.genfin.dunning.obligation.FinancialObligation;
import io.genfin.dunning.port.retry.RetryPolicy;
import io.genfin.dunning.retry.RetryHistory;
import java.time.Instant;

/**
 * Everything a {@code CollectionRule} may need to decide whether collection activity should proceed
 * for one {@link FinancialObligation} as of a given instant. Only {@code obligation} and {@code
 * asOf} are required - every other field is optional, mirroring {@code
 * io.genfin.reconciliation.rule.RuleContext}'s convention: {@code null} means "not resolved by the
 * caller", so any rule that needs it simply stays silent (reports no {@link CollectionDecision})
 * rather than failing.
 */
public record CollectionRuleContext(
    FinancialObligation obligation,
    Instant asOf,
    CollectionAttributes attributes,
    DunningCaseStatus caseStatus,
    RetryHistory retryHistory,
    RetryPolicy retryPolicy,
    GracePeriod gracePeriod)
    implements ValueObject {

  public CollectionRuleContext {
    Validate.notNull(obligation, "obligation must not be null.");
    Validate.notNull(asOf, "asOf must not be null.");
  }

  public static CollectionRuleContext of(FinancialObligation obligation, Instant asOf) {
    return new CollectionRuleContext(obligation, asOf, null, null, null, null, null);
  }

  public CollectionRuleContext withAttributes(CollectionAttributes attributes) {
    return new CollectionRuleContext(
        obligation, asOf, attributes, caseStatus, retryHistory, retryPolicy, gracePeriod);
  }

  public CollectionRuleContext withCaseStatus(DunningCaseStatus caseStatus) {
    return new CollectionRuleContext(
        obligation, asOf, attributes, caseStatus, retryHistory, retryPolicy, gracePeriod);
  }

  public CollectionRuleContext withRetryHistory(RetryHistory retryHistory) {
    return new CollectionRuleContext(
        obligation, asOf, attributes, caseStatus, retryHistory, retryPolicy, gracePeriod);
  }

  public CollectionRuleContext withRetryPolicy(RetryPolicy retryPolicy) {
    return new CollectionRuleContext(
        obligation, asOf, attributes, caseStatus, retryHistory, retryPolicy, gracePeriod);
  }

  public CollectionRuleContext withGracePeriod(GracePeriod gracePeriod) {
    return new CollectionRuleContext(
        obligation, asOf, attributes, caseStatus, retryHistory, retryPolicy, gracePeriod);
  }
}
