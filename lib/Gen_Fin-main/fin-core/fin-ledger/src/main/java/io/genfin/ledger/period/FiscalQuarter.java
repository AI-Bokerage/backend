package io.genfin.ledger.period;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.event.OccurredAt;
import io.genfin.api.validation.Validate;

/**
 * One quarter (1-4) of a {@link FiscalYear}: {@code start} (inclusive) to {@code end} (exclusive).
 */
public record FiscalQuarter(
    FiscalYear fiscalYear, int quarterNumber, OccurredAt start, OccurredAt end)
    implements ValueObject {

  private static final int MIN_QUARTER = 1;
  private static final int MAX_QUARTER = 4;

  public FiscalQuarter {
    Validate.notNull(fiscalYear, "fiscalYear must not be null.");
    Validate.argument(
        quarterNumber >= MIN_QUARTER && quarterNumber <= MAX_QUARTER,
        "quarterNumber must be between 1 and 4.");
    Validate.notNull(start, "start must not be null.");
    Validate.notNull(end, "end must not be null.");
    Validate.argument(start.value().isBefore(end.value()), "start must be before end.");
  }
}
