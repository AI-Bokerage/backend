package io.genfin.providerapi.descriptor;

import io.genfin.api.validation.Validate;

public record ProviderName(String value) {

  public ProviderName {
    Validate.notBlank(value, "value must not be blank.");
  }
}
