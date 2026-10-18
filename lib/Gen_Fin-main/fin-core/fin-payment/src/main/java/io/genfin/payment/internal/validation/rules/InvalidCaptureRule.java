package io.genfin.payment.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.payment.authorization.Capture;
import io.genfin.payment.payment.Payment;
import io.genfin.payment.validation.ValidationContext;
import io.genfin.payment.validation.ValidationIssue;
import io.genfin.payment.validation.ValidationRule;
import java.util.ArrayList;
import java.util.List;

public final class InvalidCaptureRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(Payment payment, ValidationContext context) {
    List<ValidationIssue> issues = new ArrayList<>();
    for (Capture capture : payment.captures()) {
      if (capture.amount().isNegative() || capture.amount().isZero()) {
        issues.add(
            ValidationIssue.of(
                "INVALID_CAPTURE", "Capture amount must be positive.", Severity.ERROR));
      }
    }
    return issues;
  }
}
