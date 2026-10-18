package io.genfin.payment.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.payment.authorization.Capture;
import io.genfin.payment.payment.Payment;
import io.genfin.payment.validation.ValidationContext;
import io.genfin.payment.validation.ValidationIssue;
import io.genfin.payment.validation.ValidationRule;
import java.util.ArrayList;
import java.util.List;

public final class CurrencyMismatchRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(Payment payment, ValidationContext context) {
    List<ValidationIssue> issues = new ArrayList<>();
    for (Capture capture : payment.captures()) {
      if (!capture.amount().currency().equals(payment.currency())) {
        issues.add(
            ValidationIssue.of(
                "CURRENCY_MISMATCH",
                "Capture currency "
                    + capture.amount().currency().code()
                    + " does not match payment currency "
                    + payment.currency().code()
                    + ".",
                Severity.ERROR));
      }
    }
    return issues;
  }
}
