package io.genfin.payment.gateway;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.payment.idempotency.IdempotencyKey;
import io.genfin.payment.metadata.PaymentMetadata;
import io.genfin.payment.method.PaymentMethod;
import io.genfin.payment.reference.Reference;

/**
 * A provider-neutral request to a {@code PaymentGateway} — never a raw HTTP payload or SDK request
 * object.
 */
public record GatewayRequest(
    Money amount,
    PaymentMethod method,
    Reference reference,
    IdempotencyKey idempotencyKey,
    PaymentMetadata metadata)
    implements ValueObject {

  public GatewayRequest {
    Validate.notNull(amount, "amount must not be null.");
    Validate.notNull(method, "method must not be null.");
    Validate.notNull(idempotencyKey, "idempotencyKey must not be null.");
    if (metadata == null) {
      metadata = PaymentMetadata.empty();
    }
  }
}
