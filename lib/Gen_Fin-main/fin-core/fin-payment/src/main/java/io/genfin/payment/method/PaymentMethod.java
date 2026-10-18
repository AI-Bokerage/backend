package io.genfin.payment.method;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.payment.metadata.PaymentMetadata;

/**
 * One payment method instance in use on a payment (e.g. "Visa ****4242") — never a raw provider/SDK
 * object.
 */
public record PaymentMethod(
    PaymentMethodType type, String maskedIdentifier, PaymentMetadata metadata)
    implements ValueObject {

  public PaymentMethod {
    Validate.notNull(type, "type must not be null.");
    if (metadata == null) {
      metadata = PaymentMetadata.empty();
    }
  }

  public static PaymentMethod of(PaymentMethodType type, String maskedIdentifier) {
    return new PaymentMethod(type, maskedIdentifier, PaymentMetadata.empty());
  }
}
