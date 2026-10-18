package io.genfin.ledger.port.reversal;

import io.genfin.api.exception.CoreErrorCode;
import io.genfin.api.exception.GenFinException;
import io.genfin.ledger.reversal.ReversalReason;
import io.genfin.ledger.reversal.ReversalReasonDescriptor;
import java.util.List;
import java.util.Optional;

/**
 * The set of {@link ReversalReason}s a deployment permits on a {@link
 * io.genfin.ledger.reversal.Reversal} or {@link io.genfin.ledger.reversal.Adjustment}. Mirrors
 * {@code io.genfin.refund.port.reason.RefundReasonRegistry}.
 */
public interface ReversalReasonRegistry {

  void register(ReversalReasonDescriptor descriptor);

  Optional<ReversalReasonDescriptor> find(ReversalReason reason);

  default ReversalReasonDescriptor require(ReversalReason reason) {
    return find(reason)
        .orElseThrow(
            () ->
                new GenFinException(
                    CoreErrorCode.VALIDATION_FAILED,
                    "Unsupported or disabled reversal reason: " + reason.code()));
  }

  List<ReversalReasonDescriptor> findAll();
}
