package io.genfin.pricing.calculation;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.exception.Severity;
import io.genfin.api.validation.Validate;

/**
 * One problem raised while running the Pricing Pipeline over a {@link CalculationResult} - by a
 * configured {@code io.genfin.pricing.port.calculation.PricingRule}. Mirrors {@code
 * io.genfin.ledger.posting.PostingIssue} / {@code io.genfin.ledger.validation.ValidationIssue}:
 * each engine keeps its own copy of this shape rather than sharing one across modules.
 */
public record CalculationIssue(String ruleCode, String message, Severity severity)
    implements ValueObject {

  public CalculationIssue {
    Validate.notBlank(ruleCode, "ruleCode must not be blank.");
    Validate.notBlank(message, "message must not be blank.");
    Validate.notNull(severity, "severity must not be null.");
  }

  public static CalculationIssue of(String ruleCode, String message, Severity severity) {
    return new CalculationIssue(ruleCode, message, severity);
  }
}
