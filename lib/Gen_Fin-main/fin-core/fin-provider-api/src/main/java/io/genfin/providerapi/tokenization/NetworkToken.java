package io.genfin.providerapi.tokenization;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

public record NetworkToken(String value) implements PaymentToken, ValueObject {

  public NetworkToken {
    Validate.notBlank(value, "value must not be blank.");
  }
}
