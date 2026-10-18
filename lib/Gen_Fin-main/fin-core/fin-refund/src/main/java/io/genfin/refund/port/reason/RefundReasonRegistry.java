package io.genfin.refund.port.reason;

import io.genfin.api.exception.GenFinException;
import io.genfin.refund.exception.RefundErrorCode;
import io.genfin.refund.reason.RefundReason;
import io.genfin.refund.reason.RefundReasonDescriptor;
import java.util.List;
import java.util.Optional;

public interface RefundReasonRegistry {

  void register(RefundReasonDescriptor descriptor);

  Optional<RefundReasonDescriptor> find(RefundReason reason);

  default RefundReasonDescriptor require(RefundReason reason) {
    return find(reason)
        .orElseThrow(
            () ->
                new GenFinException(
                    RefundErrorCode.INVALID_REFUND_REASON,
                    "Unsupported or disabled refund reason: " + reason.code()));
  }

  List<RefundReasonDescriptor> findAll();
}
