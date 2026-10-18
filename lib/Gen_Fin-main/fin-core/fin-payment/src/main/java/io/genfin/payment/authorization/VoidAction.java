package io.genfin.payment.authorization;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.time.Instant;

/** Voiding an authorization before it is ever captured. */
public record VoidAction(Instant voidedAt, String reason) implements ValueObject {

  public VoidAction {
    Validate.notNull(voidedAt, "voidedAt must not be null.");
  }
}
