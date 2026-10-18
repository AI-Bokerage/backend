package io.genfin.ledger.report;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/** One line of a {@link ReportSection}: a human-readable label paired with its value. */
public record ReportEntry(String label, String value) implements ValueObject {

  public ReportEntry {
    Validate.notBlank(label, "label must not be blank.");
    Validate.notNull(value, "value must not be null.");
  }

  public static ReportEntry of(String label, String value) {
    return new ReportEntry(label, value);
  }
}
