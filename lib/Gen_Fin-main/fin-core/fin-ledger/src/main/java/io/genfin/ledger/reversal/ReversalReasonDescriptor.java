package io.genfin.ledger.reversal;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * Describes a reversal reason *kind* - its human-readable label - as opposed to the bare {@link
 * ReversalReason} code carried on a {@link Reversal}/{@link Adjustment} itself. Mirrors {@code
 * io.genfin.refund.reason.RefundReasonDescriptor}.
 */
public record ReversalReasonDescriptor(ReversalReason reason, String displayName)
    implements ValueObject {

  public ReversalReasonDescriptor {
    Validate.notNull(reason, "reason must not be null.");
    Validate.notBlank(displayName, "displayName must not be blank.");
  }
}
