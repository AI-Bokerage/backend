package io.genfin.payment.intent;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.payment.idempotency.IdempotencyKey;
import io.genfin.payment.metadata.PaymentMetadata;
import io.genfin.payment.payment.PaymentPurpose;
import io.genfin.payment.reference.Reference;
import java.time.Instant;
import java.util.Optional;

/**
 * The intention to collect funds — no provider concepts (no Stripe {@code PaymentIntent}, no
 * provider ids). A {@code PaymentGateway} implementation turns this into a provider-specific call.
 */
public record PaymentIntent(
    Money amount,
    Reference invoiceReference,
    Reference customerReference,
    Instant expiresAt,
    PaymentMetadata metadata,
    PaymentPurpose purpose,
    IdempotencyKey idempotencyKey)
    implements ValueObject {

  public PaymentIntent {
    Validate.notNull(amount, "amount must not be null.");
    Validate.argument(!amount.isNegative() && !amount.isZero(), "amount must be positive.");
    Validate.notNull(purpose, "purpose must not be null.");
    Validate.notNull(idempotencyKey, "idempotencyKey must not be null.");
    if (metadata == null) {
      metadata = PaymentMetadata.empty();
    }
  }

  public Optional<Instant> expiration() {
    return Optional.ofNullable(expiresAt);
  }

  public boolean isExpired(Instant asOf) {
    return expiresAt != null && asOf.isAfter(expiresAt);
  }
}
