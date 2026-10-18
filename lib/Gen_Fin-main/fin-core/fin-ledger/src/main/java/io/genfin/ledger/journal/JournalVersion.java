package io.genfin.ledger.journal;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * A monotonically increasing version marker on a {@link JournalEntry}, incremented on every
 * lifecycle transition. Existing purely as an optimistic-concurrency guard - it never causes a
 * prior state to be overwritten or removed, {@link JournalHistory} already keeps every transition.
 */
public record JournalVersion(int value) implements ValueObject {

  public JournalVersion {
    Validate.positive(value, "value must be positive.");
  }

  public static JournalVersion initial() {
    return new JournalVersion(1);
  }

  public JournalVersion next() {
    return new JournalVersion(value + 1);
  }
}
