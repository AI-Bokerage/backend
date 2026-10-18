package io.genfin.ledger.config;

import io.genfin.api.validation.Validate;
import io.genfin.ledger.port.validation.JournalValidator;

/** The journal-validation policy set for a Ledger-engine deployment. */
public final class ValidationConfiguration {

  private final JournalValidator journalValidator;

  private ValidationConfiguration(Builder builder) {
    this.journalValidator =
        Validate.notNull(builder.journalValidator, "journalValidator must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public JournalValidator journalValidator() {
    return journalValidator;
  }

  public static final class Builder {

    private JournalValidator journalValidator;

    public Builder journalValidator(JournalValidator journalValidator) {
      this.journalValidator = journalValidator;
      return this;
    }

    public ValidationConfiguration build() {
      return new ValidationConfiguration(this);
    }
  }
}
