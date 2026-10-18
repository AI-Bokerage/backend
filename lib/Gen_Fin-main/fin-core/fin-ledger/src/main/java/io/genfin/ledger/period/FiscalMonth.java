package io.genfin.ledger.period;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.event.OccurredAt;
import io.genfin.api.validation.Validate;

/**
 * One month (1-12, counted from the start of its {@link FiscalYear}, not necessarily January) of a
 * fiscal calendar: {@code start} (inclusive) to {@code end} (exclusive).
 */
public record FiscalMonth(FiscalYear fiscalYear, int monthNumber, OccurredAt start, OccurredAt end)
    implements ValueObject {

  private static final int MIN_MONTH = 1;
  private static final int MAX_MONTH = 12;

  public FiscalMonth {
    Validate.notNull(fiscalYear, "fiscalYear must not be null.");
    Validate.argument(
        monthNumber >= MIN_MONTH && monthNumber <= MAX_MONTH,
        "monthNumber must be between 1 and 12.");
    Validate.notNull(start, "start must not be null.");
    Validate.notNull(end, "end must not be null.");
    Validate.argument(start.value().isBefore(end.value()), "start must be before end.");
  }
}
