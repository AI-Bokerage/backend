package io.genfin.payment.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.payment.payment.Payment;
import io.genfin.payment.port.method.PaymentMethodRegistry;
import io.genfin.payment.validation.ValidationContext;
import io.genfin.payment.validation.ValidationIssue;
import io.genfin.payment.validation.ValidationRule;
import java.util.List;

public final class UnsupportedPaymentMethodRule implements ValidationRule {

  private final PaymentMethodRegistry registry;

  public UnsupportedPaymentMethodRule(PaymentMethodRegistry registry) {
    this.registry = registry;
  }

  @Override
  public List<ValidationIssue> apply(Payment payment, ValidationContext context) {
    if (registry.find(payment.method().type()).isEmpty()) {
      return List.of(
          ValidationIssue.of(
              "UNSUPPORTED_PAYMENT_METHOD",
              "Payment method not supported/enabled: " + payment.method().type().code(),
              Severity.ERROR));
    }
    return List.of();
  }
}
