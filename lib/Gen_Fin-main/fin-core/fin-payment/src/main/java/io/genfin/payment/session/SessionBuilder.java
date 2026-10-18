package io.genfin.payment.session;

import io.genfin.money.money.Money;
import io.genfin.payment.id.SessionId;
import java.time.Instant;

/**
 * Builds {@link PaymentSession}s. Preferred over the canonical constructor for readability at call
 * sites.
 */
public final class SessionBuilder {

  private SessionId id;
  private Money amount;
  private Instant expiresAt;
  private SessionReference reference;

  private SessionBuilder() {}

  public static SessionBuilder newSession() {
    return new SessionBuilder();
  }

  public SessionBuilder id(SessionId id) {
    this.id = id;
    return this;
  }

  public SessionBuilder amount(Money amount) {
    this.amount = amount;
    return this;
  }

  public SessionBuilder expiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }

  public SessionBuilder reference(SessionReference reference) {
    this.reference = reference;
    return this;
  }

  public PaymentSession build() {
    return PaymentSession.open(
        id == null ? SessionId.generate() : id,
        amount,
        new SessionExpiration(expiresAt),
        reference);
  }
}
