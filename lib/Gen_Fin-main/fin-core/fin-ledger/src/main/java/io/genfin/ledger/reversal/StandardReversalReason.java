package io.genfin.ledger.reversal;

/**
 * The standard reversal/adjustment reason catalog. Mirrors {@code
 * io.genfin.refund.reason.StandardRefundReason} / {@code
 * io.genfin.reconciliation.discrepancy.StandardDiscrepancyReason}: illustrative, not exhaustive - a
 * consuming application registers its own {@link ReversalReason}s alongside or instead of these.
 */
public enum StandardReversalReason implements ReversalReason {
  DATA_ENTRY_ERROR,
  DUPLICATE_POSTING,
  INCORRECT_ACCOUNT,
  INCORRECT_AMOUNT,
  WRONG_PERIOD,
  SYSTEM_ERROR,
  AUDIT_ADJUSTMENT,
  MANUAL_CORRECTION;

  @Override
  public String code() {
    return name();
  }
}
