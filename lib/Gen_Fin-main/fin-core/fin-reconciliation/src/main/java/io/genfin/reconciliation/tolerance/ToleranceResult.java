package io.genfin.reconciliation.tolerance;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * The outcome of evaluating one {@link Tolerance} against two comparison sides: whether the
 * variance was within tolerance, how confident that call is, and a human-readable explanation a
 * caller can surface directly (e.g. on a {@code MatchResult} note).
 */
public record ToleranceResult(ToleranceOutcome outcome, double confidence, String explanation)
    implements ValueObject {

  public ToleranceResult {
    Validate.notNull(outcome, "outcome must not be null.");
    Validate.notBlank(explanation, "explanation must not be blank.");
    Validate.required(
        confidence >= 0.0 && confidence <= 1.0, "confidence must be between 0.0 and 1.0.");
  }

  public static ToleranceResult within(double confidence, String explanation) {
    return new ToleranceResult(ToleranceOutcome.WITHIN_TOLERANCE, confidence, explanation);
  }

  public static ToleranceResult exceeds(String explanation) {
    return new ToleranceResult(ToleranceOutcome.EXCEEDS_TOLERANCE, 0.0, explanation);
  }

  public static ToleranceResult notApplicable(String explanation) {
    return new ToleranceResult(ToleranceOutcome.NOT_APPLICABLE, 0.0, explanation);
  }

  public boolean isWithinTolerance() {
    return outcome == ToleranceOutcome.WITHIN_TOLERANCE;
  }
}
