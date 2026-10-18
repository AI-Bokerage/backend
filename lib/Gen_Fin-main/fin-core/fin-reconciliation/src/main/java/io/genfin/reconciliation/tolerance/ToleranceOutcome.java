package io.genfin.reconciliation.tolerance;

/** What a {@code ToleranceCalculator} decided about one {@link Tolerance} check. */
public enum ToleranceOutcome {

  /** Both sides carried a value on this dimension and the variance was within tolerance. */
  WITHIN_TOLERANCE,

  /** Both sides carried a value on this dimension and the variance exceeded tolerance. */
  EXCEEDS_TOLERANCE,

  /**
   * This dimension could not be evaluated (a side is missing the value, or currencies differ
   * outside a {@link CurrencyTolerance}'s scope) — never counted as a match nor a breach.
   */
  NOT_APPLICABLE
}
