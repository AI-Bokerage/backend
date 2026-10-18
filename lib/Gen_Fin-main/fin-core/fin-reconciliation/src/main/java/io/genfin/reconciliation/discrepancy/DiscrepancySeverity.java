package io.genfin.reconciliation.discrepancy;

/**
 * How serious a {@link Discrepancy} is, ordered from least to most severe. Distinct from {@code
 * io.genfin.reconciliation.comparison.DifferenceSeverity}: a {@code Difference} is a raw finding on
 * one dimension, a {@code Discrepancy} is the reconciliation-level classification a {@code
 * DiscrepancyPolicy} derives from it — the two scales are allowed to diverge.
 */
public enum DiscrepancySeverity {
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL
}
