package io.genfin.payment.validation;

import io.genfin.payment.idempotency.IdempotencyResult;
import io.genfin.payment.session.PaymentSession;
import java.time.Instant;

/**
 * {@code session}/{@code idempotencyResult} are optional — only rules that need them look them up.
 */
public record ValidationContext(
    Instant asOf, PaymentSession session, IdempotencyResult idempotencyResult) {

  public static ValidationContext at(Instant asOf) {
    return new ValidationContext(asOf, null, null);
  }

  public ValidationContext withSession(PaymentSession session) {
    return new ValidationContext(asOf, session, idempotencyResult);
  }

  public ValidationContext withIdempotencyResult(IdempotencyResult idempotencyResult) {
    return new ValidationContext(asOf, session, idempotencyResult);
  }
}
