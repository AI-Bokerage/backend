package io.genfin.providerapi.tokenization;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

public record CardToken(String value) implements PaymentToken, ValueObject {

  public CardToken {
    Validate.notBlank(value, "value must not be blank.");
  }
}
