package io.genfin.refund.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.refund.exception.RefundErrorCode;
import io.genfin.refund.refund.Refund;
import io.genfin.refund.validation.ValidationContext;
import io.genfin.refund.validation.ValidationIssue;
import io.genfin.refund.validation.ValidationRule;
import java.util.List;

/**
 * Only runs once {@code context.balance()} is supplied and currencies agree — a currency mismatch
 * is {@link RefundCurrencyMismatchRule}'s concern, not this rule's.
 */
public final class RefundExceedsBalanceRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(Refund refund, ValidationContext context) {
    if (context.balance() == null
        || !refund.amount().currency().equals(context.balance().paymentTotal().currency())) {
      return List.of();
    }
    if (refund.amount().compareTo(context.balance().refundableRemaining()) > 0) {
      return List.of(
          ValidationIssue.of(
              RefundErrorCode.REFUND_EXCEEDS_REFUNDABLE_BALANCE.code(),
              "Refund amount "
                  + refund.amount()
                  + " exceeds remaining refundable balance "
                  + context.balance().refundableRemaining()
                  + ".",
              Severity.ERROR));
    }
    return List.of();
  }
}
