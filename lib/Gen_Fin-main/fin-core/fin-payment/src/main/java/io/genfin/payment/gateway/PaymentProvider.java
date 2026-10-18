package io.genfin.payment.gateway;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * Descriptive metadata about the organization operating a {@code PaymentGateway} (Stripe Inc.,
 * Razorpay, ...).
 */
public record PaymentProvider(
    String providerId, String displayName, ProviderCapabilities capabilities)
    implements ValueObject {

  public PaymentProvider {
    Validate.notBlank(providerId, "providerId must not be blank.");
    Validate.notBlank(displayName, "displayName must not be blank.");
    Validate.notNull(capabilities, "capabilities must not be null.");
  }
}
