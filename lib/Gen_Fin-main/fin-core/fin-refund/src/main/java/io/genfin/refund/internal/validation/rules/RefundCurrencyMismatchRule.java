package io.genfin.refund.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.refund.exception.RefundErrorCode;
import io.genfin.refund.refund.Refund;
import io.genfin.refund.validation.ValidationContext;
import io.genfin.refund.validation.ValidationIssue;
import io.genfin.refund.validation.ValidationRule;
import java.util.List;

/** Only runs once {@code context.balance()} is supplied — nothing to compare against otherwise. */
public final class RefundCurrencyMismatchRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(Refund refund, ValidationContext context) {
    if (context.balance() != null
        && !refund.amount().currency().equals(context.balance().paymentTotal().currency())) {
      return List.of(
          ValidationIssue.of(
              RefundErrorCode.CURRENCY_MISMATCH.code(),
              "Refund currency "
                  + refund.amount().currency().code()
                  + " does not match payment currency "
                  + context.balance().paymentTotal().currency().code()
                  + ".",
              Severity.ERROR));
    }
    return List.of();
  }
}
