package io.genfin.payment.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.payment.payment.Payment;
import io.genfin.payment.validation.ValidationContext;
import io.genfin.payment.validation.ValidationIssue;
import io.genfin.payment.validation.ValidationRule;
import java.util.List;

public final class DuplicateIdempotencyKeyRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(Payment payment, ValidationContext context) {
    if (context.idempotencyResult() != null && context.idempotencyResult().isConflict()) {
      return List.of(
          ValidationIssue.of(
              "DUPLICATE_IDEMPOTENCY_KEY",
              "Idempotency key reused with a different request.",
              Severity.ERROR));
    }
    return List.of();
  }
}
