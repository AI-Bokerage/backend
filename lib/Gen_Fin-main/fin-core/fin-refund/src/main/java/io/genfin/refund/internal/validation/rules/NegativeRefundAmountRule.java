package io.genfin.refund.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.refund.refund.Refund;
import io.genfin.refund.validation.ValidationContext;
import io.genfin.refund.validation.ValidationIssue;
import io.genfin.refund.validation.ValidationRule;
import java.util.List;

/** Defense in depth: {@code Refund} already rejects a non-positive amount at construction. */
public final class NegativeRefundAmountRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(Refund refund, ValidationContext context) {
    if (refund.amount().isNegative() || refund.amount().isZero()) {
      return List.of(
          ValidationIssue.of("NEGATIVE_AMOUNT", "Refund amount must be positive.", Severity.ERROR));
    }
    return List.of();
  }
}
