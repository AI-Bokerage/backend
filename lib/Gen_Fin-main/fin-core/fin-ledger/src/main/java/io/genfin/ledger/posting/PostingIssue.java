package io.genfin.ledger.posting;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.exception.Severity;
import io.genfin.api.validation.Validate;

/**
 * One problem raised while validating a posting - by the hard balance invariant itself or by a
 * configured {@link io.genfin.ledger.port.posting.PostingRule}. Mirrors {@code
 * io.genfin.refund.validation.ValidationIssue} / {@code io.genfin.reconciliation.rule.RuleResult}.
 */
public record PostingIssue(String ruleCode, String message, Severity severity)
    implements ValueObject {

  public PostingIssue {
    Validate.notBlank(ruleCode, "ruleCode must not be blank.");
    Validate.notBlank(message, "message must not be blank.");
    Validate.notNull(severity, "severity must not be null.");
  }

  public static PostingIssue of(String ruleCode, String message, Severity severity) {
    return new PostingIssue(ruleCode, message, severity);
  }
}
