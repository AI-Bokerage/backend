package io.genfin.reconciliation.reconciliation;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.reconciliation.id.ReconciliationItemId;
import io.genfin.refund.reference.Reference;

/**
 * One record being reconciled — a link to an external record (payment, refund, settlement, bank
 * transaction, ...) via the generic {@link Reference} model, never the record itself.
 */
public record ReconciliationItem(ReconciliationItemId id, Reference source, Money amount)
    implements ValueObject {

  public ReconciliationItem {
    Validate.notNull(id, "id must not be null.");
    Validate.notNull(source, "source must not be null.");
    Validate.notNull(amount, "amount must not be null.");
  }

  public static ReconciliationItem of(Reference source, Money amount) {
    return new ReconciliationItem(ReconciliationItemId.generate(), source, amount);
  }
}
