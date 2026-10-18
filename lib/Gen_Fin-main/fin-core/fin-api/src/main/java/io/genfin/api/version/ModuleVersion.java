package io.genfin.api.version;

import io.genfin.api.validation.Validate;

/** The version of a single Gen-Fin module (e.g. {@code fin-money}), as declared by that module. */
public record ModuleVersion(String moduleName, String value) {

  public ModuleVersion {
    Validate.notBlank(moduleName, "moduleName must not be blank.");
    Validate.notBlank(value, "value must not be blank.");
  }
}
