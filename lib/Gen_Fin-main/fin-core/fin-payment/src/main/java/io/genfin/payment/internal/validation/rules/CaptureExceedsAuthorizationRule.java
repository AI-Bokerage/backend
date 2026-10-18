package io.genfin.payment.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.payment.payment.Payment;
import io.genfin.payment.validation.ValidationContext;
import io.genfin.payment.validation.ValidationIssue;
import io.genfin.payment.validation.ValidationRule;
import java.util.List;

/**
 * Defense in depth: {@code Payment.capture(...)} already rejects captures exceeding authorization
 * at mutation time.
 */
public final class CaptureExceedsAuthorizationRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(Payment payment, ValidationContext context) {
    return payment
        .authorization()
        .filter(authorization -> payment.totalCaptured().compareTo(authorization.amount()) > 0)
        .map(
            authorization ->
                List.of(
                    ValidationIssue.of(
                        "CAPTURE_EXCEEDS_AUTHORIZATION",
                        "Total captured exceeds authorized amount.",
                        Severity.ERROR)))
        .orElseGet(List::of);
  }
}
