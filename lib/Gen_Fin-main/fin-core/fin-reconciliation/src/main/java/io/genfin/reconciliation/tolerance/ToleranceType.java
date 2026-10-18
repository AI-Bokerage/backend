package io.genfin.reconciliation.tolerance;

/**
 * What dimension a {@link Tolerance} governs. Not a closed enum — implement this interface to add a
 * dimension beyond {@link StandardToleranceType}, the same way {@code
 * io.genfin.reconciliation.discrepancy.DiscrepancyCategory} is extended by callers.
 */
public interface ToleranceType {

  String code();
}
