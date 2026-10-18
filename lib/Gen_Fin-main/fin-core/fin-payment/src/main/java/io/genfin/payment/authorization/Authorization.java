package io.genfin.payment.authorization;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import java.time.Instant;

public record Authorization(Money amount, Instant authorizedAt, String gatewayReference)
    implements ValueObject {

  public Authorization {
    Validate.notNull(amount, "amount must not be null.");
    Validate.notNull(authorizedAt, "authorizedAt must not be null.");
  }
}
