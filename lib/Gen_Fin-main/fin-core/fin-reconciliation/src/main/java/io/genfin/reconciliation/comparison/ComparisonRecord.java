package io.genfin.reconciliation.comparison;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.util.CollectionUtils;
import io.genfin.money.money.Money;
import io.genfin.refund.reference.Reference;
import java.time.Instant;
import java.util.Map;

/**
 * One side of a comparison — everything a {@code ComparisonStrategy} may look at. Every field is
 * optional except {@code metadata}/{@code attributes} (empty maps stand in for "none"); a strategy
 * for a dimension that is {@code null} on either side simply reports no difference on it.
 *
 * <p>{@code status} is a plain code (e.g. a payment's or a settlement's status label) rather than
 * any one engine's status enum — reconciliation compares records from unrelated systems that will
 * never share a status type. {@code metadata} and {@code attributes} are kept as two separate maps
 * because callers commonly want to compare system-carried metadata and application-defined custom
 * attributes under different rules (e.g. different severities).
 */
public record ComparisonRecord(
    Money amount,
    Reference reference,
    String status,
    Instant timestamp,
    Map<String, String> metadata,
    Map<String, String> attributes)
    implements ValueObject {

  public ComparisonRecord {
    metadata = CollectionUtils.immutableMap(metadata);
    attributes = CollectionUtils.immutableMap(attributes);
  }

  public static ComparisonRecord of(Money amount, Reference reference) {
    return new ComparisonRecord(amount, reference, null, null, Map.of(), Map.of());
  }

  public ComparisonRecord withStatus(String status) {
    return new ComparisonRecord(amount, reference, status, timestamp, metadata, attributes);
  }

  public ComparisonRecord withTimestamp(Instant timestamp) {
    return new ComparisonRecord(amount, reference, status, timestamp, metadata, attributes);
  }

  public ComparisonRecord withMetadata(Map<String, String> metadata) {
    return new ComparisonRecord(amount, reference, status, timestamp, metadata, attributes);
  }

  public ComparisonRecord withAttributes(Map<String, String> attributes) {
    return new ComparisonRecord(amount, reference, status, timestamp, metadata, attributes);
  }
}
