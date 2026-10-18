package io.genfin.payment.session;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.payment.id.SessionId;
import io.genfin.payment.metadata.PaymentMetadata;

/**
 * A checkout session: an amount awaiting payment, bounded by an expiration. Immutable — state
 * changes return a new instance.
 */
public record PaymentSession(
    SessionId id,
    Money amount,
    SessionState state,
    SessionExpiration expiration,
    SessionReference reference,
    PaymentMetadata metadata)
    implements ValueObject {

  public PaymentSession {
    Validate.notNull(id, "id must not be null.");
    Validate.notNull(amount, "amount must not be null.");
    Validate.notNull(state, "state must not be null.");
    Validate.notNull(expiration, "expiration must not be null.");
    if (metadata == null) {
      metadata = PaymentMetadata.empty();
    }
  }

  public static PaymentSession open(
      SessionId id, Money amount, SessionExpiration expiration, SessionReference reference) {
    return new PaymentSession(
        id, amount, SessionState.OPEN, expiration, reference, PaymentMetadata.empty());
  }

  public PaymentSession complete() {
    return new PaymentSession(id, amount, SessionState.COMPLETED, expiration, reference, metadata);
  }

  public PaymentSession cancel() {
    return new PaymentSession(id, amount, SessionState.CANCELLED, expiration, reference, metadata);
  }

  public PaymentSession expire() {
    return new PaymentSession(id, amount, SessionState.EXPIRED, expiration, reference, metadata);
  }

  public boolean isOpen() {
    return state == SessionState.OPEN;
  }
}
