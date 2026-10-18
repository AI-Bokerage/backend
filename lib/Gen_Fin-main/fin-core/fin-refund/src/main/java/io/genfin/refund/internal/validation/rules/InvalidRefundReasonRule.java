package io.genfin.refund.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.api.validation.Validate;
import io.genfin.refund.exception.RefundErrorCode;
import io.genfin.refund.port.reason.RefundReasonRegistry;
import io.genfin.refund.refund.Refund;
import io.genfin.refund.validation.ValidationContext;
import io.genfin.refund.validation.ValidationIssue;
import io.genfin.refund.validation.ValidationRule;
import java.util.List;

/**
 * Only runs once {@code context.reason()} is supplied by the caller from the originating request.
 */
public final class InvalidRefundReasonRule implements ValidationRule {

  private final RefundReasonRegistry registry;

  public InvalidRefundReasonRule(RefundReasonRegistry registry) {
    this.registry = Validate.notNull(registry, "registry must not be null.");
  }

  @Override
  public List<ValidationIssue> apply(Refund refund, ValidationContext context) {
    if (context.reason() != null && registry.find(context.reason()).isEmpty()) {
      return List.of(
          ValidationIssue.of(
              RefundErrorCode.INVALID_REFUND_REASON.code(),
              "Refund reason " + context.reason().code() + " is not permitted.",
              Severity.ERROR));
    }
    return List.of();
  }
}
