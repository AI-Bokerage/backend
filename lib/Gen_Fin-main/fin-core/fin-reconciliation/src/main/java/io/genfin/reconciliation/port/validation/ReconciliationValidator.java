package io.genfin.reconciliation.port.validation;

import io.genfin.api.port.spi.Extension;
import io.genfin.reconciliation.reconciliation.Reconciliation;
import io.genfin.reconciliation.validation.ValidationContext;
import io.genfin.reconciliation.validation.ValidationResult;

/** Validates a {@link Reconciliation} — a composable, SPI-replaceable check made up of rules. */
public interface ReconciliationValidator extends Extension {

  ValidationResult validate(Reconciliation reconciliation, ValidationContext context);
}
