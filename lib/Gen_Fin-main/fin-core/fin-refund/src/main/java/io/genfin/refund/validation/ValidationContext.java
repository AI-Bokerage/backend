package io.genfin.refund.validation;

import io.genfin.refund.calculation.RefundBalance;
import io.genfin.refund.reason.RefundReason;
import java.time.Instant;

/**
 * Everything a {@link ValidationRule} may need beyond the {@code Refund} itself. Every field but
 * {@code asOf} is optional — only rules that need it look it up, and a {@code null} value simply
 * means the corresponding rule has nothing to check and reports no issue.
 *
 * <p>{@code balance} and {@code paymentDate} come from the payment being refunded, {@code reason}
 * from the originating {@code RefundRequest} (the {@code Refund} aggregate itself carries neither),
 * and {@code duplicateReference} is a pre-computed lookup the caller performs against its own
 * refund store, mirroring how fin-payment's {@code IdempotencyResult} is resolved before validation
 * runs.
 */
public record ValidationContext(
    Instant asOf,
    RefundBalance balance,
    Instant paymentDate,
    RefundReason reason,
    boolean duplicateReference) {

  public static ValidationContext at(Instant asOf) {
    return new ValidationContext(asOf, null, null, null, false);
  }

  public ValidationContext withBalance(RefundBalance balance) {
    return new ValidationContext(asOf, balance, paymentDate, reason, duplicateReference);
  }

  public ValidationContext withPaymentDate(Instant paymentDate) {
    return new ValidationContext(asOf, balance, paymentDate, reason, duplicateReference);
  }

  public ValidationContext withReason(RefundReason reason) {
    return new ValidationContext(asOf, balance, paymentDate, reason, duplicateReference);
  }

  public ValidationContext withDuplicateReference(boolean duplicateReference) {
    return new ValidationContext(asOf, balance, paymentDate, reason, duplicateReference);
  }
}
