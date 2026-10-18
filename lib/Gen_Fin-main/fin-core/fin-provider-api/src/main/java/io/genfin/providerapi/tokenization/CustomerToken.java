package io.genfin.providerapi.tokenization;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

public record CustomerToken(String value) implements PaymentToken, ValueObject {

  public CustomerToken {
    Validate.notBlank(value, "value must not be blank.");
  }
}
