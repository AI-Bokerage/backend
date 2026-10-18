package io.genfin.payment.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.payment.payment.Payment;
import io.genfin.payment.validation.ValidationContext;
import io.genfin.payment.validation.ValidationIssue;
import io.genfin.payment.validation.ValidationRule;
import java.util.List;

/**
 * Defense in depth: {@code Payment} already rejects a non-positive requested amount at
 * construction.
 */
public final class NegativeAmountRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(Payment payment, ValidationContext context) {
    if (payment.requestedAmount().isNegative() || payment.requestedAmount().isZero()) {
      return List.of(
          ValidationIssue.of(
              "NEGATIVE_AMOUNT", "Payment amount must be positive.", Severity.ERROR));
    }
    return List.of();
  }
}
