package io.genfin.reconciliation.discrepancy;

/**
 * What kind of discrepancy was found. Not a closed enum — implement this interface to add a
 * category beyond {@link StandardDiscrepancyCategory}, the same way {@code
 * io.genfin.refund.reason.RefundReason} is extended by callers.
 */
public interface DiscrepancyCategory {

  String code();
}
