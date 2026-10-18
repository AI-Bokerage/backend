package io.genfin.refund.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.api.validation.Validate;
import io.genfin.refund.exception.RefundErrorCode;
import io.genfin.refund.refund.Refund;
import io.genfin.refund.validation.ValidationContext;
import io.genfin.refund.validation.ValidationIssue;
import io.genfin.refund.validation.ValidationRule;
import java.time.Duration;
import java.util.List;

/** Only runs once {@code context.paymentDate()} is supplied. */
public final class ExpiredRefundWindowRule implements ValidationRule {

  private final Duration window;

  public ExpiredRefundWindowRule(Duration window) {
    this.window = Validate.notNull(window, "window must not be null.");
  }

  @Override
  public List<ValidationIssue> apply(Refund refund, ValidationContext context) {
    if (context.paymentDate() != null
        && context.asOf().isAfter(context.paymentDate().plus(window))) {
      return List.of(
          ValidationIssue.of(
              RefundErrorCode.REFUND_WINDOW_EXPIRED.code(),
              "Refund window of " + window + " since " + context.paymentDate() + " has elapsed.",
              Severity.ERROR));
    }
    return List.of();
  }
}
