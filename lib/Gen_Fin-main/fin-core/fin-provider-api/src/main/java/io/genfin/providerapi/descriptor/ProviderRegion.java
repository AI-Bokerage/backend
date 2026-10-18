package io.genfin.providerapi.descriptor;

import io.genfin.api.validation.Validate;

/**
 * An open, provider-defined region code (e.g. "US", "IN", "EU") — never a fixed enum since
 * providers differ.
 */
public record ProviderRegion(String code) {

  public ProviderRegion {
    Validate.notBlank(code, "code must not be blank.");
  }

  public static final ProviderRegion GLOBAL = new ProviderRegion("GLOBAL");
}
