package io.genfin.refund.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.refund.exception.RefundErrorCode;
import io.genfin.refund.refund.Refund;
import io.genfin.refund.validation.ValidationContext;
import io.genfin.refund.validation.ValidationIssue;
import io.genfin.refund.validation.ValidationRule;
import java.util.List;

/**
 * {@code context.duplicateReference()} is resolved by the caller against its own refund store
 * before validation runs, mirroring how fin-payment resolves {@code IdempotencyResult} up front.
 */
public final class DuplicateRefundReferenceRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(Refund refund, ValidationContext context) {
    if (context.duplicateReference()) {
      return List.of(
          ValidationIssue.of(
              RefundErrorCode.DUPLICATE_REFUND_REFERENCE.code(),
              "Refund reference was already used by another refund.",
              Severity.ERROR));
    }
    return List.of();
  }
}
