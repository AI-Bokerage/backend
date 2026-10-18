package io.genfin.refund.validation;

import io.genfin.refund.internal.validation.DefaultRefundRequestValidator;
import io.genfin.refund.port.reason.RefundReasonRegistry;
import io.genfin.refund.port.validation.RefundRequestValidator;

/** Factory for the default {@link RefundRequestValidator}, mirroring {@code RefundPolicies}. */
public final class RefundRequestValidators {

  private RefundRequestValidators() {}

  public static RefundRequestValidator standard(RefundReasonRegistry registry) {
    return new DefaultRefundRequestValidator(registry);
  }
}
