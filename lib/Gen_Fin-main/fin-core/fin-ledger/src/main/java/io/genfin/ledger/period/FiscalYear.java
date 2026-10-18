package io.genfin.ledger.period;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.event.OccurredAt;
import io.genfin.api.validation.Validate;

/**
 * One fiscal year of a fiscal calendar: {@code start} (inclusive) to {@code end} (exclusive).
 * Gen-Fin never assumes a calendar-year fiscal year - {@code label} and the boundaries are whatever
 * the deployment's {@link io.genfin.ledger.port.period.PeriodPolicy} computes them to be.
 */
public record FiscalYear(String label, OccurredAt start, OccurredAt end) implements ValueObject {

  public FiscalYear {
    Validate.notBlank(label, "label must not be blank.");
    Validate.notNull(start, "start must not be null.");
    Validate.notNull(end, "end must not be null.");
    Validate.argument(start.value().isBefore(end.value()), "start must be before end.");
  }
}
