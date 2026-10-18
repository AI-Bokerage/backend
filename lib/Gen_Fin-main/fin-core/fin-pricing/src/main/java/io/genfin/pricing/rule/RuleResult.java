package io.genfin.pricing.rule;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.exception.Severity;
import io.genfin.api.validation.Validate;

/**
 * One issue raised by a {@code CommercialRule} - a rule that finds nothing wrong reports no {@code
 * RuleResult} at all. Mirrors {@code io.genfin.reconciliation.rule.RuleResult} / {@code
 * io.genfin.pricing.calculation.CalculationIssue}: each engine keeps its own copy of this shape
 * rather than sharing one across modules.
 */
public record RuleResult(String ruleCode, String message, Severity severity)
    implements ValueObject {

  public RuleResult {
    Validate.notBlank(ruleCode, "ruleCode must not be blank.");
    Validate.notBlank(message, "message must not be blank.");
    Validate.notNull(severity, "severity must not be null.");
  }

  public static RuleResult of(String ruleCode, String message, Severity severity) {
    return new RuleResult(ruleCode, message, severity);
  }
}
