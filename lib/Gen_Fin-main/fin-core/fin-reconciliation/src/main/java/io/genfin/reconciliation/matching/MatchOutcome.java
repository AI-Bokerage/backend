package io.genfin.reconciliation.matching;

/** The result of comparing two {@link MatchCandidate}s under a {@code MatchingStrategy}. */
public enum MatchOutcome {

  /** The candidates are considered the same financial event. */
  MATCHED,

  /** The candidates are related but differ within an accepted variance (amount, timing, ...). */
  PARTIALLY_MATCHED,

  /** The candidates could not be linked. */
  NOT_MATCHED
}
