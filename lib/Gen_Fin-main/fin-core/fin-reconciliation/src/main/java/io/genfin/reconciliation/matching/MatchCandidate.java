package io.genfin.reconciliation.matching;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.id.ReconciliationItemId;
import io.genfin.reconciliation.reconciliation.ReconciliationItem;
import io.genfin.refund.reference.Reference;
import java.time.Instant;
import java.util.Optional;

/**
 * A {@link ReconciliationItem} as seen by the matching engine, optionally timestamped. The
 * timestamp is matching-only metadata (e.g. a bank posting date) — it is never carried by the
 * {@link ReconciliationItem} itself, which stays a plain link to the source record.
 */
public record MatchCandidate(ReconciliationItem item, Instant occurredAt) implements ValueObject {

  public MatchCandidate {
    Validate.notNull(item, "item must not be null.");
  }

  public static MatchCandidate of(ReconciliationItem item) {
    return new MatchCandidate(item, null);
  }

  public static MatchCandidate of(ReconciliationItem item, Instant occurredAt) {
    return new MatchCandidate(item, Validate.notNull(occurredAt, "occurredAt must not be null."));
  }

  public ReconciliationItemId id() {
    return item.id();
  }

  public Money amount() {
    return item.amount();
  }

  public Reference reference() {
    return item.source();
  }

  public Optional<Instant> timestamp() {
    return Optional.ofNullable(occurredAt);
  }

  /**
   * Adapts this candidate to a {@link ComparisonRecord} so a {@code ComparisonPolicy} can explain
   * exactly what differs between two candidates even when a {@code MatchingStrategy} still calls
   * them a match (e.g. a tolerance or partial match). Comparison stays a lower-level building block
   * with no knowledge of matching — this conversion is the one place that direction is bridged.
   */
  public ComparisonRecord toComparisonRecord() {
    return ComparisonRecord.of(amount(), reference()).withTimestamp(occurredAt);
  }
}
