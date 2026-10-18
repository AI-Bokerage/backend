package io.genfin.refund.policy;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.refund.exception.RefundErrorCode;

/** One reason a {@link io.genfin.refund.port.policy.RefundPolicy} denied a refund request. */
public record PolicyViolation(RefundErrorCode code, String message) implements ValueObject {

  public PolicyViolation {
    Validate.notNull(code, "code must not be null.");
    Validate.notBlank(message, "message must not be blank.");
  }
}
