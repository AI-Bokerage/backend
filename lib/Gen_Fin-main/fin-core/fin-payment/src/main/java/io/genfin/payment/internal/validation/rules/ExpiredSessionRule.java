package io.genfin.payment.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.payment.payment.Payment;
import io.genfin.payment.validation.ValidationContext;
import io.genfin.payment.validation.ValidationIssue;
import io.genfin.payment.validation.ValidationRule;
import java.util.List;

public final class ExpiredSessionRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(Payment payment, ValidationContext context) {
    if (context.session() != null && context.session().expiration().isExpired(context.asOf())) {
      return List.of(
          ValidationIssue.of("EXPIRED_SESSION", "Payment session has expired.", Severity.ERROR));
    }
    return List.of();
  }
}
