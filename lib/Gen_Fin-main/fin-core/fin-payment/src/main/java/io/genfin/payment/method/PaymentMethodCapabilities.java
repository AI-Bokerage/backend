package io.genfin.payment.method;

import io.genfin.api.domain.ValueObject;

public record PaymentMethodCapabilities(
    boolean supportsAuthorization,
    boolean supportsCapture,
    boolean supportsPartialCapture,
    boolean supportsRefund,
    boolean supportsRecurring)
    implements ValueObject {

  public static PaymentMethodCapabilities fullyCapable() {
    return new PaymentMethodCapabilities(true, true, true, true, true);
  }

  public static PaymentMethodCapabilities captureOnlyNoRefund() {
    return new PaymentMethodCapabilities(false, true, false, false, false);
  }
}
