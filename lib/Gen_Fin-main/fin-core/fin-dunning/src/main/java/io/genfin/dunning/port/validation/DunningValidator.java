package io.genfin.dunning.port.validation;

import io.genfin.api.port.spi.Extension;
import io.genfin.dunning.validation.ValidationContext;
import io.genfin.dunning.validation.ValidationResult;

/**
 * Validates whichever combination of {@code DunningPolicy}/{@code RetryPlan}/{@code ReminderPlan}/
 * {@code EscalationDecision}/{@code CollectionPlan} a {@link ValidationContext} carries - a
 * composable, SPI-replaceable check made up of {@link
 * io.genfin.dunning.validation.ValidationRule}s. Enforces fin-dunning's own structural/sanity
 * invariants (e.g. a resolved policy's max retries is non-negative and its backoff strategy
 * actually yields a positive delay); it never enforces application-specific business thresholds,
 * those belong to the application's own rules.
 */
public interface DunningValidator extends Extension {

  ValidationResult validate(ValidationContext context);
}
