package io.genfin.ledger.fact;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.event.OccurredAt;
import io.genfin.api.util.CollectionUtils;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.refund.reference.Reference;
import java.util.Map;

/**
 * A normalized "something happened that needs posting" event, the single shape the Posting Engine
 * consumes regardless of whether it originated from Invoice, Payment, Refund, or Reconciliation.
 * Upstream modules are adapted into this shape at the boundary; the Ledger's posting logic never
 * depends on any upstream module's own event types.
 */
public record FinancialFact(
    Money amount,
    Reference reference,
    FinancialFactType factType,
    OccurredAt occurredAt,
    Map<String, String> metadata)
    implements ValueObject {

  public FinancialFact {
    Validate.notNull(amount, "amount must not be null.");
    Validate.notNull(reference, "reference must not be null.");
    Validate.notNull(factType, "factType must not be null.");
    Validate.notNull(occurredAt, "occurredAt must not be null.");
    metadata = CollectionUtils.immutableMap(metadata);
  }

  public static FinancialFact of(
      Money amount, Reference reference, FinancialFactType factType, OccurredAt occurredAt) {
    return new FinancialFact(amount, reference, factType, occurredAt, Map.of());
  }
}
