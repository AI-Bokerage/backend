package io.genfin.refund.refund;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * A human-facing refund number (e.g. shown on receipts/support tickets), distinct from {@link
 * io.genfin.refund.id.RefundId}, which is the internal identity.
 */
public record RefundNumber(String value) implements ValueObject {

  public RefundNumber {
    Validate.notBlank(value, "value must not be null or blank.");
  }

  public static RefundNumber of(String value) {
    return new RefundNumber(value);
  }
}
