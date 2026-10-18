package io.genfin.ledger.period;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.exception.Severity;
import io.genfin.api.validation.Validate;

/**
 * One problem raised by {@link PeriodValidator}. Mirrors {@code
 * io.genfin.ledger.posting.PostingIssue}.
 */
public record PeriodIssue(String ruleCode, String message, Severity severity)
    implements ValueObject {

  public PeriodIssue {
    Validate.notBlank(ruleCode, "ruleCode must not be blank.");
    Validate.notBlank(message, "message must not be blank.");
    Validate.notNull(severity, "severity must not be null.");
  }

  public static PeriodIssue of(String ruleCode, String message, Severity severity) {
    return new PeriodIssue(ruleCode, message, severity);
  }
}
