package io.genfin.ledger.port.reversal;

import io.genfin.ledger.reversal.ReversalReasonDescriptor;
import java.util.List;

/**
 * Supplies the {@link ReversalReasonDescriptor}s a {@link ReversalReasonRegistry} should start out
 * with. Mirrors {@code io.genfin.refund.port.reason.RefundReasonProvider}.
 */
public interface ReversalReasonProvider {

  List<ReversalReasonDescriptor> provide();
}
