package io.genfin.ledger.reversal;

/**
 * Why a {@link Reversal} or {@link Adjustment} was made. An open taxonomy - Gen-Fin ships {@link
 * StandardReversalReason} but a consuming application may implement this interface itself for
 * reasons of its own, exactly as {@code io.genfin.refund.reason.RefundReason} does for refunds.
 */
public interface ReversalReason {

  String code();
}
