package io.genfin.payment.idempotency;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

public record IdempotencyKey(String value) implements ValueObject {

  public IdempotencyKey {
    Validate.notBlank(value, "value must not be blank.");
  }

  public static IdempotencyKey of(String value) {
    return new IdempotencyKey(value);
  }
}
