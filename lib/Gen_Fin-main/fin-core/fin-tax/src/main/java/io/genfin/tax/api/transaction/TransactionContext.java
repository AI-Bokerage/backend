package io.genfin.tax.api.transaction;

import io.genfin.api.validation.Validate;
import java.time.Instant;

/**
 * Transaction-level facts the resolver needs beyond the two parties' profiles — currently just
 * timing (rates may change over time; a future version can resolve rates as of this instant).
 * Deliberately excludes anything CPMS-specific: no invoice id, no project id, no reference.
 */
public record TransactionContext(Instant occurredAt) {

  public TransactionContext {
    Validate.notNull(occurredAt, "occurredAt must not be null.");
  }

  public static TransactionContext at(Instant occurredAt) {
    return new TransactionContext(occurredAt);
  }
}
