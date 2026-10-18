package io.genfin.payment.method;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * Describes a method *kind* — its capabilities — as opposed to {@link PaymentMethod}, one instance
 * in use on a payment.
 */
public record PaymentMethodDescriptor(
    PaymentMethodType type, String displayName, PaymentMethodCapabilities capabilities)
    implements ValueObject {

  public PaymentMethodDescriptor {
    Validate.notNull(type, "type must not be null.");
    Validate.notBlank(displayName, "displayName must not be blank.");
    Validate.notNull(capabilities, "capabilities must not be null.");
  }
}
