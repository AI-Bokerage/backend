package io.genfin.reconciliation.discrepancy;

/**
 * Root cause code carried on a {@link Discrepancy}. Not a closed enum — implement this interface to
 * add a reason beyond {@link StandardDiscrepancyReason}, the same way {@code
 * io.genfin.refund.reason.RefundReason} is extended by callers.
 */
public interface DiscrepancyReason {

  String code();
}
