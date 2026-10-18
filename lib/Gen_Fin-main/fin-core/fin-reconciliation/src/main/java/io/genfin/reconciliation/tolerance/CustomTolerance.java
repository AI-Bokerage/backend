package io.genfin.reconciliation.tolerance;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * The generic extension point for a tolerance dimension this module does not model structurally
 * (e.g. a fee-schedule-aware variance, or a business-specific rounding rule). Carries only the name
 * of a rule registered against {@code
 * io.genfin.reconciliation.port.tolerance.CustomToleranceRuleRegistry} — the rule itself, not this
 * record, decides what "within tolerance" means.
 */
public record CustomTolerance(String ruleName) implements Tolerance, ValueObject {

  public CustomTolerance {
    Validate.notBlank(ruleName, "ruleName must not be blank.");
  }

  public static CustomTolerance of(String ruleName) {
    return new CustomTolerance(ruleName);
  }

  @Override
  public ToleranceType type() {
    return StandardToleranceType.CUSTOM;
  }
}
