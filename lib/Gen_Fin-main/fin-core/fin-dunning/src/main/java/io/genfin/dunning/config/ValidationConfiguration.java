package io.genfin.dunning.config;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.port.validation.DunningValidator;
import io.genfin.dunning.validation.Validators;

/**
 * Immutable, builder-based configuration for the Validation concern: the resolved {@link
 * DunningValidator} an application (or fin-dunning's own {@link Validators#standard()}) supplies to
 * check a resolved policy/plan/decision's structural sanity before the caller acts on it.
 */
public final class ValidationConfiguration {

  private final DunningValidator dunningValidator;

  private ValidationConfiguration(Builder builder) {
    this.dunningValidator =
        Validate.notNull(builder.dunningValidator, "dunningValidator must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public DunningValidator dunningValidator() {
    return dunningValidator;
  }

  public static final class Builder {

    private DunningValidator dunningValidator = Validators.standard();

    public Builder dunningValidator(DunningValidator dunningValidator) {
      this.dunningValidator = dunningValidator;
      return this;
    }

    public ValidationConfiguration build() {
      return new ValidationConfiguration(this);
    }
  }
}
