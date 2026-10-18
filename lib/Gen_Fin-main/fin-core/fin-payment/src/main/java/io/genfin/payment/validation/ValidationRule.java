package io.genfin.payment.validation;

import io.genfin.payment.payment.Payment;
import java.util.List;

@FunctionalInterface
public interface ValidationRule {

  List<ValidationIssue> apply(Payment payment, ValidationContext context);
}
