package io.genfin.providerapi.descriptor;

import io.genfin.api.validation.Validate;

public record ProviderVersion(String value) {

  public ProviderVersion {
    Validate.notBlank(value, "value must not be blank.");
  }
}
