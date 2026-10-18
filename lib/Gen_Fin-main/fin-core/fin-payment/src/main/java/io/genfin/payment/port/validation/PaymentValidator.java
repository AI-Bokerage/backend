package io.genfin.payment.port.validation;

import io.genfin.api.port.spi.Extension;
import io.genfin.payment.payment.Payment;
import io.genfin.payment.validation.ValidationContext;
import io.genfin.payment.validation.ValidationResult;

public interface PaymentValidator extends Extension {

  ValidationResult validate(Payment payment, ValidationContext context);
}
