package io.genfin.refund.refund;

/**
 * Which way funds move for this refund: {@code OUTBOUND} (paying money back to the customer, the
 * common case) or {@code INBOUND} (recovering funds already paid out, e.g. a chargeback reversal).
 */
public enum RefundDirection {
  OUTBOUND,
  INBOUND
}
