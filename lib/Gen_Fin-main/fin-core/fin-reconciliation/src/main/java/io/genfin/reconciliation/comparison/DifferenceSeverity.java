package io.genfin.reconciliation.comparison;

/** How serious a found {@link Difference} is, ordered from least to most severe. */
public enum DifferenceSeverity {

  /** Present but explainable/expected (e.g. an amount gap still inside tolerance). */
  MINOR,

  /** Requires review but does not by itself block reconciliation. */
  MAJOR,

  /** Blocks reconciliation of the two records until resolved (e.g. currency mismatch). */
  CRITICAL
}
