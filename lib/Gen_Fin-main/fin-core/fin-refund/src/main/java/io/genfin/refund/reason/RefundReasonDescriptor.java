package io.genfin.refund.reason;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * Describes a refund reason *kind* — its human-readable label — as opposed to the bare {@link
 * RefundReason} code used on a refund itself.
 */
public record RefundReasonDescriptor(RefundReason reason, String displayName)
    implements ValueObject {

  public RefundReasonDescriptor {
    Validate.notNull(reason, "reason must not be null.");
    Validate.notBlank(displayName, "displayName must not be blank.");
  }
}
