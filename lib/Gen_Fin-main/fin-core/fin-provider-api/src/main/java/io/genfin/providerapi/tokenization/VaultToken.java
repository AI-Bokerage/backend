package io.genfin.providerapi.tokenization;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

public record VaultToken(String value) implements PaymentToken, ValueObject {

  public VaultToken {
    Validate.notBlank(value, "value must not be blank.");
  }
}
